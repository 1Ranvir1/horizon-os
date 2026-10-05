package dev.gtos.domain

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

data class AppId(val component: String, val userSerial: Long) {
    val stableKey: String get() = "$userSerial:$component"
}

data class LaunchableApp(val id: AppId, val label: String, val packageName: String)

object AppCatalog {
    fun search(apps: List<LaunchableApp>, query: String): List<LaunchableApp> {
        val terms = normalize(query).split(Regex("\\s+")).filter(String::isNotBlank)
        if (terms.isEmpty()) return apps
        return apps.filter { app ->
            val haystack = normalize("${app.label} ${app.packageName}")
            terms.all(haystack::contains)
        }
    }

    fun sorted(apps: List<LaunchableApp>, locale: Locale = Locale.getDefault()): List<LaunchableApp> {
        val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
        return apps.distinctBy { it.id }.sortedWith { a, b ->
            val labelOrder = collator.compare(a.label, b.label)
            if (labelOrder != 0) labelOrder else a.id.stableKey.compareTo(b.id.stableKey)
        }
    }

    private fun normalize(value: String): String = Normalizer
        .normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .trim()
}
