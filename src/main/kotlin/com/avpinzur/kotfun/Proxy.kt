package com.avpinzur.kotfun

import net.sf.cglib.proxy.Enhancer
import net.sf.cglib.proxy.MethodInterceptor
import java.lang.reflect.InvocationTargetException
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.kotlinFunction

sealed interface Invocation<in I, out R> {
    val function: KFunction<R>
    val arguments: List<Any?>
}

data class SimpleInvocation<I, R>(
    override val function: KFunction<R>,
    override val arguments: List<Any?>,
) : Invocation<I, R> {
    fun apply(target: I): R = try {
        val targetArguments = listOf(target) + arguments
        function.call(*targetArguments.toTypedArray())
    } catch (exc: InvocationTargetException) {
        throw exc.cause!!
    }
}

data class SuspendInvocation<I, R>(
    override val function: KFunction<R>,
    override val arguments: List<Any?>,
) : Invocation<I, R> {
    suspend fun apply(target: I): R =
        suspendCoroutine { continuation ->
            try {
                val targetArguments = listOf(target) + arguments + continuation
                function.call(*targetArguments.toTypedArray())
            } catch (exc: InvocationTargetException) {
                continuation.resumeWithException(exc.cause!!)
            }
        }
}

interface InvocationHandler<I> {
    fun handle (invocation: SimpleInvocation<I, *>): Any?
    suspend fun handle(invocation: SuspendInvocation<I, *>): Any?
}

inline fun <reified I> InvocationHandler<I>.toInterceptor() = MethodInterceptor { _, method, args, _ ->
    val function = method.kotlinFunction ?: throw Exception("Not a Kotlin function.")
    if (!function.isSuspend) {
        handle(SimpleInvocation(function, args.asList()))
    } else {
        @Suppress("UNCHECKED_CAST")
        val continuation = args.last() as Continuation<Any?>
        val otherArgs = args.dropLast(1)
        val handler: suspend (() -> Any?) = { handle(SuspendInvocation(function, otherArgs)) }

        handler.startCoroutine(continuation)
        COROUTINE_SUSPENDED
    }
}

inline fun <reified I> proxy(handler: InvocationHandler<I>): I =
    Enhancer().run{
        setInterfaces(arrayOf(I::class.java))
        setCallback(handler.toInterceptor())
        create() as I
    }
