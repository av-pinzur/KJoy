package com.avpinzur.kjoy

import java.lang.reflect.InvocationTargetException
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.reflect.KFunction

sealed interface Invocation<in I> {
    val function: KFunction<Any?>
    val arguments: List<Any?>

    val description: String get() = "${function.name}(${arguments.joinToString()})"
}

data class SimpleInvocation<I>(
    override val function: KFunction<Any?>,
    override val arguments: List<Any?>
) : Invocation<I> {
    fun apply(target: I): Any? =
        try {
            val targetArguments = listOf(target) + arguments
            function.call(*targetArguments.toTypedArray())
        } catch (exc: InvocationTargetException) {
            throw exc.cause!!
        }
}

data class SuspendInvocation<I>(
    override val function: KFunction<Any?>,
    override val arguments: List<Any?>
) : Invocation<I> {
    suspend fun apply(target: I): Any? =
        suspendCoroutine { continuation ->
            try {
                val targetArguments = listOf(target) + arguments + continuation
                function.call(*targetArguments.toTypedArray())
            } catch (exc: InvocationTargetException) {
                continuation.resumeWithException(exc.cause!!)
            }
        }
}
