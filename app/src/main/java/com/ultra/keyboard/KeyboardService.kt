package com.ultra.keyboard

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.gridlayout.widget.GridLayout

/**
 * Ultra Keyboard - 3x4 (dial-pad stil) tastatura za Android, multi-tap unos,
 * podrška za srpsku latinicu i ćirilicu, i puna pristupačnost za TalkBack:
 * svaki taster je pravi View (Button), sa opisom (contentDescription) i
 * najavom (announceForAccessibility) svaki put kad se slovo promeni.
 */
class KeyboardService : InputMethodService() {

    companion object {
        private const val TAG = "UltraKeyboard"
    }

    private fun logD(msg: String) {
        Log.d(TAG, msg)
        AppLogger.append(this, TAG, msg)
    }

    // ---------------- Zvuk i vibracija pri kucanju ----------------
    private lateinit var audioManager: AudioManager
    private lateinit var prefs: SharedPreferences
    private var feedbackEnabled = true
    private var tapMode = TapMode.ONE_TAP
    private var keySize = KeySize.MEDIUM
    private var keyMode = KeyMode.LETTERS

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        prefs = getSharedPreferences("ultra_keyboard_prefs", MODE_PRIVATE)
        feedbackEnabled = prefs.getBoolean("feedback_enabled", true)
        tapMode = TapMode.entries.getOrElse(prefs.getInt("tap_mode", 0)) { TapMode.ONE_TAP }
        keySize = KeySize.entries.getOrElse(prefs.getInt("key_size", 1)) { KeySize.MEDIUM }
    }

    private fun setFeedbackEnabled(enabled: Boolean) {
        feedbackEnabled = enabled
        prefs.edit().putBoolean("feedback_enabled", enabled).apply()
    }

    private fun setTapMode(mode: TapMode) {
        tapMode = mode
        prefs.edit().putInt("tap_mode", mode.ordinal).apply()
    }

    private fun setKeySize(size: KeySize) {
        keySize = size
        prefs.edit().putInt("key_size", size.ordinal).apply()
    }

    // ---------------- Glasovni unos ----------------
    private var speechRecognizer: SpeechRecognizer? = null

    private fun startVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            startActivity(
                Intent(this, VoicePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, R.string.voice_unavailable, Toast.LENGTH_LONG).show()
            return
        }

        finalizePending()
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isCyrillic) "sr-RS" else "sr-Latn-RS")
            // Traži automatsku interpunkciju/formatiranje od prepoznavača, ako
            // ga uređaj/servis podržava (Android 13+, zavisi od Google app
            // verzije). Na uređajima koji ovo ne podržavaju, ekstra se prosto
            // ignoriše - bezbedno je uvek je poslati.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
            }
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                updatePreview("🎤 " + getString(R.string.voice_listening))
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                updatePreview("⏳ " + getString(R.string.voice_processing))
            }
            override fun onError(error: Int) {
                logD("voice input error=$error")
                updatePreview("")
                speechRecognizer?.destroy()
                speechRecognizer = null
            }
            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    currentInputConnection?.commitText("$text ", 1)
                }
                updatePreview("")
                speechRecognizer?.destroy()
                speechRecognizer = null
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    /**
     * Kratka vibracija + odgovarajući zvuk klika, poštuju sistemska
     * podešavanja (ako korisnik ima isključenu vibraciju/zvuk na telefonu,
     * ovo se automatski ne oglašava - Android to sam filtrira).
     */
    private fun giveFeedback(view: View, keyTag: Any) {
        if (!feedbackEnabled) return
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        val soundEffect = when (keyTag) {
            "kBack", "panelBackspace" -> AudioManager.FX_KEYPRESS_DELETE
            "kEnter" -> AudioManager.FX_KEYPRESS_RETURN
            "k0", "panelSpace" -> AudioManager.FX_KEYPRESS_SPACEBAR
            else -> AudioManager.FX_KEYPRESS_STANDARD
        }
        audioManager.playSoundEffect(soundEffect)
    }

    private enum class ShiftState { OFF, ONCE, CAPS_LOCK }
    private enum class Panel { LETTERS, SYMBOLS, EMOJI, SETTINGS, INFO, NUMERIC }
    private enum class TapMode { ONE_TAP, LIFT_TO_TYPE, STANDARD }
    private enum class KeyMode { LETTERS, NUMBERS, SYMBOLS }
    private enum class KeySize(
        val rowHeightDp: Int,
        val controlHeightDp: Int,
        val mainTextSp: Float,
        val specialTextSp: Float
    ) {
        SMALL(80, 42, 20f, 16f),
        MEDIUM(112, 52, 26f, 22f),
        LARGE(142, 62, 32f, 26f)
    }

    private val handler = Handler(Looper.getMainLooper())

    private var isCyrillic = false
    private var shiftState = ShiftState.OFF

    // Stanje multi-tap ciklusa
    private var pendingKeyId: Int = -1
    private var pendingOptions: List<Char> = emptyList()
    private var pendingIndex: Int = -1
    private var pendingUpper: Boolean = false
    private val finalizeRunnable = Runnable { finalizePending() }
    private val cycleTimeoutMs = 450L

    private var currentPanel = Panel.LETTERS

    // Za "podigni prst da otkucaš" - da ne bi trebalo dupli-dodir sa TalkBack-om.
    // Kad je TalkBack (explore by touch) uključen, koristimo ISKLJUČIVO hover
    // putanju; kad nije, ISKLJUČIVO običan klik. Ranije su radila oba
    // istovremeno i dolazilo je do dupliranja (2 koraka po jednom dodiru).
    private val hoverHandler = Handler(Looper.getMainLooper())
    private var activeHoverKey: Any? = null
    private val lastActivation = mutableMapOf<Any, Long>()

    private fun isTouchExplorationOn(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
        return am?.isTouchExplorationEnabled == true
    }

    /**
     * Vezuje taster prema trenutno izabranom režimu brzine (Podešavanja):
     * - ONE_TAP (podrazumevano): reaguje ODMAH čim TalkBack javi da si
     *   dodirnuo taster (HOVER_ENTER) - ne čeka se podizanje prsta. Najbrže.
     * - LIFT_TO_TYPE: reaguje kad podigneš prst sa tastera (stari mehanizam) -
     *   malo sporije, ali čuješ slovo pre nego što se potvrdi.
     * - STANDARD: čist TalkBack dupli-dodir, bez naše magije - najsporije,
     *   ali identično svakoj drugoj aplikaciji na telefonu.
     * Van TalkBack-a (sighted kucanje), uvek radi običan, trenutan klik.
     */
    private fun wireAccessibleKey(view: View, keyTag: Any, forceStandard: Boolean = false, action: () -> Unit) {
        view.setOnClickListener {
            val explore = isTouchExplorationOn()
            val now = System.currentTimeMillis()
            val last = lastActivation[keyTag] ?: 0L
            logD("CLICK tag=$keyTag explore=$explore mode=$tapMode sinceLastActivation=${now - last}")
            if (explore && now - last < 200) return@setOnClickListener
            lastActivation[keyTag] = now
            giveFeedback(view, keyTag)
            action()
        }

        // Sirov dodir - eksperimentalno, za slučaj da neki uređaj/verzija
        // Android-a ipak propusti sirove dodire. Do sada (test sa drugarom)
        // nikad nije okinulo dok je TalkBack uključen - ostavljeno kao
        // bezopasna rezerva, ne konzumuje event.
        view.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val inBounds = event.x >= 0 && event.x <= v.width && event.y >= 0 && event.y <= v.height
                if (inBounds && isTouchExplorationOn() && !forceStandard && tapMode == TapMode.ONE_TAP) {
                    val now2 = System.currentTimeMillis()
                    val last2 = lastActivation[keyTag] ?: 0L
                    if (now2 - last2 > 150) {
                        lastActivation[keyTag] = now2
                        activeHoverKey = null
                        logD("RAW_UP -> ACTION FIRED (instant) tag=$keyTag")
                        giveFeedback(v, keyTag)
                        action()
                    }
                }
            }
            false
        }

        view.setOnHoverListener { _, event ->
            val effectiveMode = if (forceStandard) TapMode.STANDARD else tapMode
            when (event.action) {
                MotionEvent.ACTION_HOVER_ENTER -> {
                    activeHoverKey = keyTag
                    if (effectiveMode == TapMode.ONE_TAP) {
                        val now = System.currentTimeMillis()
                        val last = lastActivation[keyTag] ?: 0L
                        logD("HOVER_ENTER(brzo) tag=$keyTag sinceLastActivation=${now - last}")
                        if (now - last > 250) {
                            lastActivation[keyTag] = now
                            giveFeedback(view, keyTag)
                            logD("HOVER_ENTER -> ACTION FIRED (brzo) tag=$keyTag")
                            action()
                        }
                    } else {
                        logD("HOVER_ENTER tag=$keyTag")
                    }
                }
                MotionEvent.ACTION_HOVER_EXIT -> {
                    if (effectiveMode != TapMode.LIFT_TO_TYPE) return@setOnHoverListener false
                    logD("HOVER_EXIT tag=$keyTag (scheduling check)")
                    hoverHandler.postDelayed({
                        val now = System.currentTimeMillis()
                        val last = lastActivation[keyTag] ?: 0L
                        val stillActive = activeHoverKey == keyTag
                        val debounceOk = now - last > 200
                        logD("HOVER_EXIT check tag=$keyTag stillActive=$stillActive debounceOk=$debounceOk (activeHoverKey=$activeHoverKey)")
                        if (stillActive && debounceOk) {
                            activeHoverKey = null
                            lastActivation[keyTag] = now
                            logD("HOVER_EXIT -> ACTION FIRED tag=$keyTag")
                            giveFeedback(view, keyTag)
                            action()
                        }
                    }, 50)
                }
            }
            false
        }
    }


    // View reference-i (letters keyboard)
    private var previewText: TextView? = null
    private var keySettings: Button? = null
    private var letterKeys: Map<Int, Button> = emptyMap()
    private var key1Btn: Button? = null
    private var key0Btn: Button? = null
    private var keyStarBtn: Button? = null
    private var keyHashBtn: Button? = null

    override fun onCreateInputView(): View {
        return inflateLettersView()
    }

    private fun inflateLettersView(): View {
        currentPanel = Panel.LETTERS
        val v = LayoutInflater.from(this).inflate(R.layout.keyboard_view, null)

        previewText = v.findViewById(R.id.previewText)
        val keyEnter: Button = v.findViewById(R.id.keyEnter)
        val keyVoice: Button = v.findViewById(R.id.keyVoice)
        val keyEmoji: Button = v.findViewById(R.id.keyEmoji)
        keySettings = v.findViewById(R.id.keySettings)
        val keyBackspace: Button = v.findViewById(R.id.keyBackspace)

        letterKeys = mapOf(
            2 to v.findViewById(R.id.key2),
            3 to v.findViewById(R.id.key3),
            4 to v.findViewById(R.id.key4),
            5 to v.findViewById(R.id.key5),
            6 to v.findViewById(R.id.key6),
            7 to v.findViewById(R.id.key7),
            8 to v.findViewById(R.id.key8),
            9 to v.findViewById(R.id.key9)
        )
        val key1: Button = v.findViewById(R.id.key1)
        val key0: Button = v.findViewById(R.id.key0)
        val keyStar: Button = v.findViewById(R.id.keyStar)
        val keyHash: Button = v.findViewById(R.id.keyHash)
        key1Btn = key1
        key0Btn = key0
        keyStarBtn = keyStar
        keyHashBtn = keyHash

        refreshLetterLabelsAndDescriptions()

        // Tasteri 2-9: slova (samo u modu LETTERS)
        for ((id, btn) in letterKeys) {
            wireAccessibleKey(btn, id) {
                if (keyMode != KeyMode.LETTERS) return@wireAccessibleKey
                val options = KeyMaps.mapFor(isCyrillic)[id] ?: return@wireAccessibleKey
                handleOptionsKey(id, options, applyCase = true)
            }
        }

        // Taster 1: interpunkcija (LETTERS) ili direktan broj/simbol (ostali modovi)
        wireAccessibleKey(key1, "k1") { handleDigitRowKey(1) { handleOptionsKey(1, KeyMaps.PUNCT_1, applyCase = false) } }

        // Taster 0: razmak (LETTERS) ili direktan broj/simbol (ostali modovi)
        wireAccessibleKey(key0, "k0") { handleDigitRowKey(0) { handleOptionsKey(0, KeyMaps.KEY_0, applyCase = false) } }

        // Zvezdica: kruži Slova -> Brojevi -> Simboli -> Slova...
        wireAccessibleKey(keyStar, "kStar") { onStarPressed() }
        keyStar.setOnLongClickListener {
            if (keyMode == KeyMode.SYMBOLS) {
                finalizePending()
                setInputView(inflateSymbolsPanel())
            }
            true
        }
        updateStarDescription(keyStar)

        // # (taraba): Šift - kratko veliko sledeće slovo, dugo Caps Lock
        wireShiftKey(keyHash)

        // Glasovni unos (gornji red, iznad trojke)
        wireAccessibleKey(keyVoice, "kVoice") { startVoiceInput() }
        keyVoice.contentDescription = getString(R.string.key_voice_desc)

        // Enter (gornji red, iznad jedinice)
        wireAccessibleKey(keyEnter, "kEnter") {
            finalizePending()
            currentInputConnection?.sendKeyEvent(
                android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER)
            )
            currentInputConnection?.commitText("\n", 1)
        }
        keyEnter.contentDescription = getString(R.string.key_enter_desc)

        // Emotikoni - namerno traži pravi dupli-dodir (lako se pobrka sa Brisanjem)
        wireAccessibleKey(keyEmoji, "kEmoji", forceStandard = true) {
            finalizePending()
            setInputView(inflateEmojiPanel())
        }
        keyEmoji.contentDescription = getString(R.string.key_emoji_desc)

        // Brisanje (gornji red, iznad dvojke)
        wireAccessibleKey(keyBackspace, "kBack") {
            finalizePending()
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
        keyBackspace.contentDescription = getString(R.string.key_backspace_desc)
        // Dugi pritisak (ili "dupli-dodir-pa-drži" pod TalkBack-om) pokreće
        // brzo brisanje u nizu. Ako sistem uspe da nam javi kad si pustio
        // prst, staje odmah; ako ne (čest slučaj pod TalkBack-om), staje
        // samo posle sigurnosnog broja karaktera, da se ne obriše sve slučajno.
        keyBackspace.setOnLongClickListener {
            startBackspaceRepeat()
            true
        }
        keyBackspace.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                stopBackspaceRepeat()
            }
            false
        }

        // Podešavanja - namerno traži pravi dupli-dodir
        keySettings?.let {
            wireAccessibleKey(it, "kSettings", forceStandard = true) { setInputView(inflateSettingsPanel()) }
        }
        keySettings?.contentDescription = getString(R.string.key_settings_desc)

        applyKeySize(v)

        return v
    }

    /** Zajednička logika za tastere 1-9,0 van LETTERS moda: direktan unos broja ili simbola. */
    private fun handleDigitRowKey(id: Int, lettersAction: () -> Unit) {
        when (keyMode) {
            KeyMode.LETTERS -> lettersAction()
            KeyMode.NUMBERS -> {
                finalizePending()
                KeyMaps.NUMBERS[id]?.let { currentInputConnection?.commitText(it.toString(), 1) }
            }
            KeyMode.SYMBOLS -> {
                finalizePending()
                KeyMaps.SYMBOLS_MODE[id]?.let { currentInputConnection?.commitText(it.toString(), 1) }
            }
        }
    }

    private fun wireShiftKey(keyHash: Button) {
        wireAccessibleKey(keyHash, "kHash") { onShiftShortPress() }
        keyHash.setOnLongClickListener {
            onShiftLongPress()
            true
        }
        updateShiftUi()
    }

    private fun onStarPressed() {
        finalizePending()
        keyMode = when (keyMode) {
            KeyMode.LETTERS -> KeyMode.NUMBERS
            KeyMode.NUMBERS -> KeyMode.SYMBOLS
            KeyMode.SYMBOLS -> KeyMode.LETTERS
        }
        refreshLetterLabelsAndDescriptions()
    }

    private fun updateStarDescription(keyStar: Button) {
        val label = when (keyMode) {
            KeyMode.LETTERS -> getString(R.string.key_star_desc)
            KeyMode.NUMBERS -> getString(R.string.mode_numbers)
            KeyMode.SYMBOLS -> getString(R.string.mode_symbols)
        }
        keyStar.contentDescription = label
        keyStar.announceForAccessibility(label)
    }

    // ---------------- Brzo brisanje u nizu (drži Brisanje) ----------------
    private val backspaceHandler = Handler(Looper.getMainLooper())
    private var backspaceRepeatRunnable: Runnable? = null

    private fun startBackspaceRepeat() {
        stopBackspaceRepeat()
        finalizePending()
        var count = 0
        val r = object : Runnable {
            override fun run() {
                currentInputConnection?.deleteSurroundingText(1, 0)
                count++
                if (count < 80) { // sigurnosna granica (~6-7 sekundi brisanja)
                    backspaceHandler.postDelayed(this, 80)
                }
            }
        }
        backspaceRepeatRunnable = r
        backspaceHandler.post(r)
    }

    private fun stopBackspaceRepeat() {
        backspaceRepeatRunnable?.let { backspaceHandler.removeCallbacks(it) }
        backspaceRepeatRunnable = null
    }

    private fun onShiftShortPress() {
        shiftState = when (shiftState) {
            ShiftState.OFF -> ShiftState.ONCE
            ShiftState.ONCE -> ShiftState.OFF
            ShiftState.CAPS_LOCK -> ShiftState.OFF
        }
        updateShiftUi()
    }

    private fun onShiftLongPress() {
        shiftState = if (shiftState == ShiftState.CAPS_LOCK) ShiftState.OFF else ShiftState.CAPS_LOCK
        updateShiftUi()
    }

    private fun updateShiftUi() {
        val label = when (shiftState) {
            ShiftState.OFF -> "malo slovo"
            ShiftState.ONCE -> "veliko sledeće slovo"
            ShiftState.CAPS_LOCK -> "sva velika slova"
        }
        keyHashBtn?.text = if (shiftState == ShiftState.CAPS_LOCK) "#\n⇧⇧" else "#\n⇧"
        keyHashBtn?.contentDescription = getString(R.string.key_shift_desc) + ": " + label
        keyHashBtn?.announceForAccessibility(label)
    }

    /**
     * Primenjuje izabranu veličinu (Mala/Srednja/Velika) na sve redove i
     * tekst tastature - poziva se posle svakog inflateLettersView().
     */
    private fun applyKeySize(v: View) {
        val density = resources.displayMetrics.density
        val rowPx = (keySize.rowHeightDp * density).toInt()
        val controlPx = (keySize.controlHeightDp * density).toInt()

        val gridRows = listOf(R.id.gridRow1, R.id.gridRow2, R.id.gridRow3, R.id.gridRow4)
        for (id in gridRows) {
            v.findViewById<View>(id)?.let { it.layoutParams = it.layoutParams.apply { height = rowPx } }
        }
        val controlRows = listOf(R.id.controlRow1, R.id.controlRow2)
        for (id in controlRows) {
            v.findViewById<View>(id)?.let { it.layoutParams = it.layoutParams.apply { height = controlPx } }
        }

        for (btn in letterKeys.values) btn.textSize = keySize.mainTextSp
        key1Btn?.textSize = keySize.mainTextSp
        key0Btn?.textSize = keySize.mainTextSp
        keyStarBtn?.textSize = keySize.mainTextSp
        keyHashBtn?.textSize = keySize.mainTextSp

        val specialIds = listOf(R.id.keyEnter, R.id.keyBackspace, R.id.keyVoice, R.id.keyEmoji, R.id.keySettings)
        for (id in specialIds) {
            (v.findViewById<View>(id) as? Button)?.textSize = keySize.specialTextSp
        }
    }

    private fun onScriptPressed() {
        finalizePending()
        isCyrillic = !isCyrillic
        refreshLetterLabelsAndDescriptions()
    }

    private fun refreshLetterLabelsAndDescriptions() {
        when (keyMode) {
            KeyMode.LETTERS -> {
                val map = KeyMaps.mapFor(isCyrillic)
                for ((id, btn) in letterKeys) {
                    val letters = map[id]?.dropLast(1)?.joinToString("") ?: ""
                    btn.text = "$id\n$letters"
                    btn.contentDescription = "Taster $id: ${map[id]?.dropLast(1)?.joinToString(", ")}"
                }
                key1Btn?.text = "1"
                key0Btn?.text = "0\nrazmak"
            }
            KeyMode.NUMBERS -> {
                for ((id, btn) in letterKeys) {
                    val d = KeyMaps.NUMBERS[id]?.toString() ?: ""
                    btn.text = d
                    btn.contentDescription = "Taster $id: $d"
                }
                key1Btn?.text = KeyMaps.NUMBERS[1]?.toString() ?: "1"
                key0Btn?.text = KeyMaps.NUMBERS[0]?.toString() ?: "0"
            }
            KeyMode.SYMBOLS -> {
                for ((id, btn) in letterKeys) {
                    val s = KeyMaps.SYMBOLS_MODE[id]?.toString() ?: ""
                    btn.text = s
                    btn.contentDescription = "Taster $id: $s"
                }
                key1Btn?.text = KeyMaps.SYMBOLS_MODE[1]?.toString() ?: ""
                key0Btn?.text = KeyMaps.SYMBOLS_MODE[0]?.toString() ?: ""
            }
        }
        keyStarBtn?.let { updateStarDescription(it) }
    }

    /**
     * Zajednička logika za sve tastere koji rade po principu multi-tap
     * ciklusa (slova, interpunkcija na 1, brzi simboli na *, razmak na 0).
     */
    private fun handleOptionsKey(keyId: Int, options: List<Char>, applyCase: Boolean) {
        val ic = currentInputConnection ?: return
        logD("handleOptionsKey called keyId=$keyId pendingKeyId(before)=$pendingKeyId pendingIndex(before)=$pendingIndex")

        if (pendingKeyId == keyId) {
            // Isti taster u nizu -> obriši poslednji ubačeni znak i pređi na sledeći u ciklusu
            ic.deleteSurroundingText(1, 0)
            pendingIndex = (pendingIndex + 1) % options.size
        } else {
            finalizePending()
            pendingKeyId = keyId
            pendingOptions = options
            pendingIndex = 0
            pendingUpper = applyCase && shiftState != ShiftState.OFF
            if (applyCase && shiftState == ShiftState.ONCE) {
                // single-shot shift se troši na početku ove sekvence
                shiftState = ShiftState.OFF
                updateShiftUi()
            }
        }

        var ch = pendingOptions[pendingIndex]
        if (pendingUpper) ch = ch.uppercaseChar()
        ic.commitText(ch.toString(), 1)
        logD("handleOptionsKey result keyId=$keyId pendingIndex(after)=$pendingIndex char=$ch")

        updatePreview(ch.toString())
        maybeAutoCapitalize()

        val timeout = when {
            !isTouchExplorationOn() -> cycleTimeoutMs
            tapMode == TapMode.ONE_TAP -> 500L
            tapMode == TapMode.LIFT_TO_TYPE -> 1000L
            else -> 1500L // STANDARD (pravi dupli-dodir) - treba malo više vremena
        }
        handler.removeCallbacks(finalizeRunnable)
        handler.postDelayed(finalizeRunnable, timeout)
    }

    private fun finalizePending() {
        logD("finalizePending (was pendingKeyId=$pendingKeyId pendingIndex=$pendingIndex)")
        handler.removeCallbacks(finalizeRunnable)
        pendingKeyId = -1
        pendingOptions = emptyList()
        pendingIndex = -1
        previewText?.text = ""
    }

    private fun updatePreview(text: String) {
        previewText?.text = text
        val toAnnounce = if (text.length == 1 && text[0].isUpperCase()) {
            "veliko ${text[0]}"
        } else {
            text
        }
        previewText?.announceForAccessibility(toAnnounce)
    }

    // ---------------- Podešavanja / Pomoć / O programu ----------------

    private fun inflateSettingsPanel(): View {
        currentPanel = Panel.SETTINGS
        val v = LayoutInflater.from(this).inflate(R.layout.settings_panel, null)

        val btnScript: Button = v.findViewById(R.id.btnToggleScript)
        val btnFeedback: Button = v.findViewById(R.id.btnToggleFeedback)
        val btnTapMode: Button = v.findViewById(R.id.btnToggleTapMode)
        val btnSize: Button = v.findViewById(R.id.btnToggleSize)
        val btnLog: Button = v.findViewById(R.id.btnSendLog)
        val btnHelp: Button = v.findViewById(R.id.btnHelp)
        val btnAbout: Button = v.findViewById(R.id.btnAbout)
        val btnBack: Button = v.findViewById(R.id.btnSettingsBack)
        val btnBackTop: Button = v.findViewById(R.id.btnSettingsBackTop)

        fun refreshScriptLabel() {
            btnScript.text = getString(
                if (isCyrillic) R.string.settings_script_cyrillic else R.string.settings_script_latin
            )
        }
        refreshScriptLabel()

        fun refreshFeedbackLabel() {
            btnFeedback.text = getString(
                if (feedbackEnabled) R.string.settings_feedback_on else R.string.settings_feedback_off
            )
        }
        refreshFeedbackLabel()

        fun refreshTapModeLabel() {
            btnTapMode.text = getString(
                when (tapMode) {
                    TapMode.ONE_TAP -> R.string.settings_tap_one
                    TapMode.LIFT_TO_TYPE -> R.string.settings_tap_lift
                    TapMode.STANDARD -> R.string.settings_tap_standard
                }
            )
        }
        refreshTapModeLabel()

        fun refreshSizeLabel() {
            btnSize.text = getString(
                when (keySize) {
                    KeySize.SMALL -> R.string.settings_size_small
                    KeySize.MEDIUM -> R.string.settings_size_medium
                    KeySize.LARGE -> R.string.settings_size_large
                }
            )
        }
        refreshSizeLabel()

        wireAccessibleKey(btnScript, "sScript") {
            onScriptPressed()
            refreshScriptLabel()
            btnScript.announceForAccessibility(btnScript.text)
        }
        wireAccessibleKey(btnFeedback, "sFeedback") {
            setFeedbackEnabled(!feedbackEnabled)
            refreshFeedbackLabel()
            btnFeedback.announceForAccessibility(btnFeedback.text)
        }
        wireAccessibleKey(btnTapMode, "sTapMode") {
            val next = when (tapMode) {
                TapMode.ONE_TAP -> TapMode.LIFT_TO_TYPE
                TapMode.LIFT_TO_TYPE -> TapMode.STANDARD
                TapMode.STANDARD -> TapMode.ONE_TAP
            }
            setTapMode(next)
            refreshTapModeLabel()
            btnTapMode.announceForAccessibility(btnTapMode.text)
        }
        wireAccessibleKey(btnSize, "sSize") {
            val next = when (keySize) {
                KeySize.SMALL -> KeySize.MEDIUM
                KeySize.MEDIUM -> KeySize.LARGE
                KeySize.LARGE -> KeySize.SMALL
            }
            setKeySize(next)
            refreshSizeLabel()
            btnSize.announceForAccessibility(btnSize.text)
        }
        wireAccessibleKey(btnLog, "sLog") { sendLogFile() }
        wireAccessibleKey(btnHelp, "sHelp") {
            setInputView(inflateInfoPanel(getString(R.string.help_text)))
        }
        wireAccessibleKey(btnAbout, "sAbout") {
            setInputView(inflateInfoPanel(getString(R.string.about_text)))
        }
        wireAccessibleKey(btnBack, "sBack") { setInputView(inflateLettersView()) }
        wireAccessibleKey(btnBackTop, "sBackTop") { setInputView(inflateLettersView()) }

        return v
    }

    private fun inflateInfoPanel(text: String): View {
        currentPanel = Panel.INFO
        val v = LayoutInflater.from(this).inflate(R.layout.info_panel, null)
        val infoText: TextView = v.findViewById(R.id.infoText)
        infoText.text = text
        val backBtn: Button = v.findViewById(R.id.btnInfoBack)
        val backBtnTop: Button = v.findViewById(R.id.btnInfoBackTop)
        wireAccessibleKey(backBtn, "infoBack") { setInputView(inflateSettingsPanel()) }
        wireAccessibleKey(backBtnTop, "infoBackTop") { setInputView(inflateSettingsPanel()) }
        return v
    }

    /** Deli log fajl preko standardnog Android "pošalji" menija (WhatsApp, mejl, itd.) */
    private fun sendLogFile() {
        try {
            val file = AppLogger.getLogFile(this)
            if (!file.exists() || file.length() == 0L) {
                android.widget.Toast.makeText(this, R.string.log_missing, android.widget.Toast.LENGTH_LONG).show()
                return
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(sendIntent, getString(R.string.settings_send_log)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(chooser)
        } catch (e: Exception) {
            logD("sendLogFile failed: ${e.message}")
        }
    }

    private fun inflateSymbolsPanel(): View {
        currentPanel = Panel.SYMBOLS
        val v = LayoutInflater.from(this).inflate(R.layout.panel_view, null)
        val grid: GridLayout = v.findViewById(R.id.gridContainer)
        populateGrid(grid, KeyMaps.SYMBOLS_FULL.map { it to it })
        wirePanelCommonButtons(v)
        return v
    }

    private fun inflateEmojiPanel(): View {
        currentPanel = Panel.EMOJI
        val v = LayoutInflater.from(this).inflate(R.layout.panel_view, null)
        val grid: GridLayout = v.findViewById(R.id.gridContainer)
        populateGrid(grid, KeyMaps.EMOJI)
        wirePanelCommonButtons(v)
        return v
    }

    private fun populateGrid(grid: GridLayout, items: List<Pair<String, String>>) {
        grid.removeAllViews()
        for ((symbol, description) in items) {
            val btn = Button(this)
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = LinearLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(4, 4, 4, 4)
            }
            btn.layoutParams = params
            btn.text = symbol
            btn.contentDescription = description
            btn.setBackgroundResource(R.drawable.key_background)
            btn.setTextColor(resources.getColor(R.color.key_text, theme))
            btn.textSize = 20f
            btn.minimumHeight = 130
            wireAccessibleKey(btn, "grid_$symbol") {
                currentInputConnection?.commitText(symbol, 1)
                btn.announceForAccessibility("uneto $description")
            }
            grid.addView(btn)
        }
    }

    private fun wirePanelCommonButtons(v: View) {
        val backBtn: Button = v.findViewById(R.id.keyBackToLetters)
        val backspaceBtn: Button = v.findViewById(R.id.keyPanelBackspace)
        val spaceBtn: Button = v.findViewById(R.id.keyPanelSpace)

        backBtn.contentDescription = getString(R.string.key_back_to_letters_desc)
        wireAccessibleKey(backBtn, "panelBack") { setInputView(inflateLettersView()) }

        backspaceBtn.contentDescription = getString(R.string.key_backspace_desc)
        wireAccessibleKey(backspaceBtn, "panelBackspace") { currentInputConnection?.deleteSurroundingText(1, 0) }

        spaceBtn.contentDescription = getString(R.string.key_space_desc)
        wireAccessibleKey(spaceBtn, "panelSpace") { currentInputConnection?.commitText(" ", 1) }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        finalizePending()
        shiftState = ShiftState.OFF
        maybeAutoCapitalize()
        updateShiftUi()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        finalizePending()
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        stopBackspaceRepeat()
        super.onDestroy()
    }

    /**
     * Automatsko veliko slovo: na početku praznog polja, i posle tačke/
     * upitnika/uzvičnika + razmaka - kao na svakoj standardnoj tastaturi.
     * Ne dira Caps Lock ako je već uključen.
     */
    private fun maybeAutoCapitalize() {
        if (shiftState == ShiftState.CAPS_LOCK) return
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
        val shouldCap = before.isEmpty() ||
            (before.length >= 2 && before[before.length - 1] == ' ' && before[before.length - 2] in ".!?")
        if (shouldCap && shiftState == ShiftState.OFF) {
            shiftState = ShiftState.ONCE
            updateShiftUi()
        }
    }
}
