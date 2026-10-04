package com.example.arsen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast

object DeepSeekBridge {

    const val PACKAGE_NAME = "com.deepseek.chat"
    const val WEB_URL = "https://chat.deepseek.com"
    const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.deepseek.chat"

    fun isDeepSeekInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Копирует промпт в буфер обмена и запускает DeepSeek с фото и промптом
     */
    fun sendRequestToDeepSeek(
        context: Context,
        photoUri: Uri?,
        prompt: String,
        onPromptCopied: (String) -> Unit
    ) {
        // 1. Копируем промпт в буфер обмена для мгновенной вставки
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("DeepSeek Prompt", prompt)
        clipboard.setPrimaryClip(clip)
        onPromptCopied(prompt)

        val isInstalled = isDeepSeekInstalled(context)

        // 2. Если фото есть, пробуем отправить фото + текст через ACTION_SEND
        if (photoUri != null) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, photoUri)
                putExtra(Intent.EXTRA_TEXT, prompt)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (isInstalled) {
                    setPackage(PACKAGE_NAME)
                }
            }

            try {
                context.startActivity(shareIntent)
                Toast.makeText(
                    context,
                    "Фото и промпт отправлены в DeepSeek! Скопируйте ответ и вернитесь сюда.",
                    Toast.LENGTH_LONG
                ).show()
                return
            } catch (_: Exception) {
                // Если не получилось передать через целевой пакет, пробуем через общий chooser
                try {
                    val chooser = Intent.createChooser(shareIntent, "Отправить в DeepSeek")
                    context.startActivity(chooser)
                    return
                } catch (_: Exception) {}
            }
        }

        // 3. Если фото нет или прямое вложение не открылось — запускаем сам DeepSeek
        if (isInstalled) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)
            if (launchIntent != null) {
                context.startActivity(launchIntent)
                Toast.makeText(
                    context,
                    "Промпт скопирован! Прикрепите фото и вставьте промпт в DeepSeek.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        // 4. Если приложение DeepSeek не установлено — предлагаем открыть в браузере или скачать
        try {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(WEB_URL))
            context.startActivity(webIntent)
            Toast.makeText(
                context,
                "Промпт скопирован в буфер! Вставьте в DeepSeek Web.",
                Toast.LENGTH_LONG
            ).show()
        } catch (_: Exception) {
            Toast.makeText(context, "Не удалось открыть DeepSeek", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Проверяет буфер обмена на наличие ответа из DeepSeek
     */
    fun getClipboardContent(context: Context, lastSentPrompt: String): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString()?.trim()
            if (!text.isNullOrBlank() && text != lastSentPrompt.trim()) {
                return text
            }
        }
        return null
    }

    /**
     * Открывает Google Play для установки DeepSeek
     */
    fun openPlayStore(context: Context) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_URL)))
        } catch (_: Exception) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WEB_URL)))
        }
    }
}
