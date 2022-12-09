package com.avpinzur.kjoy

import net.sf.cglib.proxy.Enhancer
import net.sf.cglib.proxy.MethodInterceptor
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.startCoroutine
import kotlin.reflect.jvm.kotlinFunction

inline fun <reified I : Any> I.proxy(decorator: Decorator<SimpleInvocation<I>, SuspendInvocation<I>, Any?>): I =
    proxy(InvocationDecorator.of(decorator))

inline fun <reified I : Any> I.proxy(decorator: InvocationDecorator<I>): I =
    proxy(InvocationHandler.of(this).decoratedBy(decorator))

inline fun <reified I> proxy(handler: InvocationHandler<I>): I =
    Enhancer().run {
        setInterfaces(arrayOf(I::class.java))
        setCallback(handler.toInterceptor())
        create() as I
    }

inline fun <reified I> InvocationHandler<I>.toInterceptor() = MethodInterceptor { _, method, args, _ ->
    val function = method.kotlinFunction ?: throw Exception("Not a Kotlin function.")
    if (!function.isSuspend) {
        handleSimple(SimpleInvocation(function, args.asList()))
    } else {
        @Suppress("UNCHECKED_CAST")
        val continuation = args.last() as Continuation<Any?>
        val otherArgs = args.dropLast(1)
        val handler: suspend (() -> Any?) = { handleSuspend(SuspendInvocation(function, otherArgs)) }

        handler.startCoroutine(continuation)
        COROUTINE_SUSPENDED
    }
}
