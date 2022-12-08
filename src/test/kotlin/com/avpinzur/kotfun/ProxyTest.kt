package com.avpinzur.kotfun

import org.junit.jupiter.api.Test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isInstanceOf
import kotlinx.coroutines.*

private suspend fun <R> reallySuspend(block: () -> R): R = coroutineScope {
    val (a, b) = async { delay(100) } to async { delay(100) }
    a.await() to b.await()
    block()
}

internal class ProxyTest {
    @Test
    fun `it passes simple calls through to the handler`() {
        var invocation: Invocation<Subject, *>? = null
        val target = ConcreteSubject()
        val subject = proxy(NonSuspendHandler { invocation = it; it.apply(target) })
        assertThat(subject.simpleFunction(3, 4)).isEqualTo(7)
        assertThat(invocation).isEqualTo(SimpleInvocation(Subject::simpleFunction, listOf(3, 4)))
    }

    @Test
    fun `it throws simple exception back to the caller`() {
        val target = ThrowingSubject()
        val subject = proxy<Subject>(NonSuspendHandler { it.apply(target) })
        assertThat { subject.simpleFunction(3, 4) }.isFailure().isInstanceOf(SampleException::class)
    }

    @Test
    fun `it passes suspend calls through to the handler`(): Unit = runBlocking {
        var invocation: SuspendInvocation<Subject, *>? = null
        val target = ConcreteSubject()
        val subject = proxy(
            SuspendOnlyHandler {
                invocation = it
                it.apply(target)
            }
        )
        assertThat(subject.suspendFunction(3, 4)).isEqualTo(12)
        assertThat(invocation).isEqualTo(SuspendInvocation(Subject::suspendFunction, listOf(3, 4)))
    }

    @Test
    fun `it throws suspend exception back to the caller`(): Unit = runBlocking {
        val target = ThrowingSubject()
        val subject = proxy<Subject>(
            SuspendOnlyHandler {
                it.apply(target)
            }
        )
        assertThat { subject.suspendFunction(3, 4) }.isFailure().isInstanceOf(SampleException::class)
    }

    interface Subject {
        fun simpleFunction(x: Int, y: Int): Int
        suspend fun suspendFunction(x: Int, y: Int): Int
    }

    class ConcreteSubject : Subject {
        override fun simpleFunction(x: Int, y: Int): Int = x + y
        override suspend fun suspendFunction(x: Int, y: Int): Int = reallySuspend { x * y }
    }

    class ThrowingSubject : Subject {
        override fun simpleFunction(x: Int, y: Int): Int = throw SampleException()
        override suspend fun suspendFunction(x: Int, y: Int): Int = reallySuspend { throw SampleException() }
    }

    class SampleException : Exception("Simulated error for testing.")

    class NonSuspendHandler<I>(private val handler: (SimpleInvocation<I, *>) -> Any?) : InvocationHandler<I> {
        override fun handle(invocation: SimpleInvocation<I, *>): Any? = handler(invocation)
        override suspend fun handle(invocation: SuspendInvocation<I, *>): Any? = TODO()
    }

    class SuspendOnlyHandler<I>(private val handler: suspend (SuspendInvocation<I, *>) -> Any?) : InvocationHandler<I> {
        override fun handle(invocation: SimpleInvocation<I, *>): Any? = TODO()
        override suspend fun handle(invocation: SuspendInvocation<I, *>): Any? = handler(invocation)
    }
}
