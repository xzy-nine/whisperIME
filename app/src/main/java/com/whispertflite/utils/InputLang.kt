package com.whispertflite.utils

class InputLang private constructor(private var code: String?, private var id: Long) {
    companion object {
        @JvmStatic
        val langList: ArrayList<InputLang>
            // Initialize the list of input language objects
            get() {
                val inputLangList =
                    java.util.ArrayList<InputLang>()
                inputLangList.add(InputLang("en", 50259))
                inputLangList.add(InputLang("zh", 50260))
                inputLangList.add(InputLang("de", 50261))
                inputLangList.add(InputLang("es", 50262))
                inputLangList.add(InputLang("ru", 50263))
                inputLangList.add(InputLang("ko", 50264))
                inputLangList.add(InputLang("fr", 50265))
                inputLangList.add(InputLang("ja", 50266))
                inputLangList.add(InputLang("pt", 50267))
                inputLangList.add(InputLang("tr", 50268))
                inputLangList.add(InputLang("pl", 50269))
                inputLangList.add(InputLang("ca", 50270))
                inputLangList.add(InputLang("nl", 50271))
                inputLangList.add(InputLang("ar", 50272))
                inputLangList.add(InputLang("sv", 50273))
                inputLangList.add(InputLang("it", 50274))
                inputLangList.add(InputLang("id", 50275))
                inputLangList.add(InputLang("hi", 50276))
                inputLangList.add(InputLang("fi", 50277))
                inputLangList.add(InputLang("vi", 50278))
                inputLangList.add(InputLang("he", 50279))
                inputLangList.add(InputLang("uk", 50280))
                inputLangList.add(InputLang("el", 50281))
                inputLangList.add(InputLang("ms", 50282))
                inputLangList.add(InputLang("cs", 50283))
                inputLangList.add(InputLang("ro", 50284))
                inputLangList.add(InputLang("da", 50285))
                inputLangList.add(InputLang("hu", 50286))
                inputLangList.add(InputLang("ta", 50287))
                inputLangList.add(InputLang("no", 50288))
                inputLangList.add(InputLang("th", 50289))
                inputLangList.add(InputLang("ur", 50290))
                inputLangList.add(InputLang("hr", 50291))
                inputLangList.add(InputLang("bg", 50292))
                inputLangList.add(InputLang("lt", 50293))
                inputLangList.add(InputLang("la", 50294))
                inputLangList.add(InputLang("mi", 50295))
                inputLangList.add(InputLang("ml", 50296))
                inputLangList.add(InputLang("cy", 50297))
                inputLangList.add(InputLang("sk", 50298))
                inputLangList.add(InputLang("te", 50299))
                inputLangList.add(InputLang("fa", 50300))
                inputLangList.add(InputLang("lv", 50301))
                inputLangList.add(InputLang("bn", 50302))
                inputLangList.add(InputLang("sr", 50303))
                inputLangList.add(InputLang("az", 50304))
                inputLangList.add(InputLang("sl", 50305))
                inputLangList.add(InputLang("kn", 50306))
                inputLangList.add(InputLang("et", 50307))
                inputLangList.add(InputLang("mk", 50308))
                inputLangList.add(InputLang("br", 50309))
                inputLangList.add(InputLang("eu", 50310))
                inputLangList.add(InputLang("is", 50311))
                inputLangList.add(InputLang("hy", 50312))
                inputLangList.add(InputLang("ne", 50313))
                inputLangList.add(InputLang("mn", 50314))
                inputLangList.add(InputLang("bs", 50315))
                inputLangList.add(InputLang("kk", 50316))
                inputLangList.add(InputLang("sq", 50317))
                inputLangList.add(InputLang("sw", 50318))
                inputLangList.add(InputLang("gl", 50319))
                inputLangList.add(InputLang("mr", 50320))
                inputLangList.add(InputLang("pa", 50321))
                inputLangList.add(InputLang("si", 50322))
                inputLangList.add(InputLang("km", 50323))
                inputLangList.add(InputLang("sn", 50324))
                inputLangList.add(InputLang("yo", 50325))
                inputLangList.add(InputLang("so", 50326))
                inputLangList.add(InputLang("af", 50327))
                inputLangList.add(InputLang("oc", 50328))
                inputLangList.add(InputLang("ka", 50329))
                inputLangList.add(InputLang("be", 50330))
                inputLangList.add(InputLang("tg", 50331))
                inputLangList.add(InputLang("sd", 50332))
                inputLangList.add(InputLang("gu", 50333))
                inputLangList.add(InputLang("am", 50334))
                inputLangList.add(InputLang("yi", 50335))
                inputLangList.add(InputLang("lo", 50336))
                inputLangList.add(InputLang("uz", 50337))
                inputLangList.add(InputLang("fo", 50338))
                inputLangList.add(InputLang("ht", 50339))
                inputLangList.add(InputLang("ps", 50340))
                inputLangList.add(InputLang("tk", 50341))
                inputLangList.add(InputLang("nn", 50342))
                inputLangList.add(InputLang("mt", 50343))
                inputLangList.add(InputLang("sa", 50344))
                inputLangList.add(InputLang("lb", 50345))
                inputLangList.add(InputLang("my", 50346))
                inputLangList.add(InputLang("bo", 50347))
                inputLangList.add(InputLang("tl", 50348))
                inputLangList.add(InputLang("mg", 50349))
                inputLangList.add(InputLang("as", 50350))
                inputLangList.add(InputLang("tt", 50351))
                inputLangList.add(InputLang("haw", 50352))
                inputLangList.add(InputLang("ln", 50353))
                inputLangList.add(InputLang("ha", 50354))
                inputLangList.add(InputLang("ba", 50355))
                inputLangList.add(InputLang("jw", 50356))
                inputLangList.add(InputLang("su", 50357))

                return inputLangList
            }

        fun getLanguageCodeById(inputLangList: java.util.ArrayList<InputLang>, id: Int): String? {
            for (lang in inputLangList) {
                if (lang.id == id.toLong()) {
                    return lang.code
                }
            }
            return ""
        }

        fun getIdForLanguage(list: java.util.ArrayList<InputLang>, language: String?): Int {
            for (inputLang in list) {
                if (inputLang.code == language) {
                    return inputLang.id.toInt()
                }
            }
            return -1 // Return -1 if the language is not found
        }
    }
}
