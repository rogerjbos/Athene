package io.github.athene

import android.app.Application
import android.content.Context
import io.github.athene.data.ContentDao
import io.github.athene.data.ContentDatabase
import io.github.athene.data.Settings
import io.github.athene.data.UserDao
import io.github.athene.data.UserDatabase
import io.github.athene.speech.Speaker

class AtheneApplication : Application() {
    val content: ContentDao by lazy { ContentDatabase.open(this).dao() }

    val user: UserDao by lazy { UserDatabase.open(this).dao() }

    val settings: Settings by lazy { Settings(this) }

    /** Shared text-to-speech; the engine is only started on first use. */
    val speaker: Speaker by lazy { Speaker(this) }
}

val Context.athene: AtheneApplication get() = applicationContext as AtheneApplication
