package com.lalilu.lmedia.domain.model

import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

interface LItem : Identifiable, Describable, Extensible
