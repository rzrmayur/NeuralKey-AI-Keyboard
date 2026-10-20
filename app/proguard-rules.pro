# Proguard rules for NeuralKey AI Keyboard

# Keep annotations used at runtime
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses

# Keep kotlinx.serialization @Serializable classes
-keep,includedescriptorclasses class com.supakeyboard.neuralkey.data.network.model.**$$serializer { *; }
-keepclassmembers class com.supakeyboard.neuralkey.data.network.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.supakeyboard.neuralkey.data.network.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all data classes in the network model package
-keep class com.supakeyboard.neuralkey.data.network.model.ChatCompletionRequest { *; }
-keep class com.supakeyboard.neuralkey.data.network.model.ChatMessageDto { *; }
-keep class com.supakeyboard.neuralkey.data.network.model.ProviderRoutingDto { *; }
-keep class com.supakeyboard.neuralkey.data.network.model.ChatCompletionChunkDto { *; }
-keep class com.supakeyboard.neuralkey.data.network.model.ChunkChoiceDto { *; }
-keep class com.supakeyboard.neuralkey.data.network.model.ChunkDeltaDto { *; }
-keep class com.supakeyboard.neuralkey.data.network.model.KeyAuthResponse { *; }
-keep class com.supakeyboard.neuralkey.data.network.model.KeyDataDto { *; }
-keep class com.supakeyboard.neuralkey.data.network.model.RateLimitDto { *; }
-keep class com.supakeyboard.neuralkey.data.network.model.OpenRouterModelsResponse { *; }
-keep class com.supakeyboard.neuralkey.data.network.model.OpenRouterModelDto { *; }
-keep class com.supakeyboard.neuralkey.data.network.model.ArchitectureDto { *; }
-keep class com.supakeyboard.neuralkey.data.network.model.PricingDto { *; }
-keep class com.supakeyboard.neuralkey.data.network.model.TopProviderDto { *; }

# Keep Room entities
-keep class com.supakeyboard.neuralkey.data.db.entity.** { *; }

# Keep Room DAOs
-keep class com.supakeyboard.neuralkey.data.db.dao.** { *; }

# Keep Room database class
-keep class com.supakeyboard.neuralkey.data.db.NeuralKeyDatabase { *; }

# Keep Room generated members
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
