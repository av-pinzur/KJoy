package com.avpinzur.kjoy

interface InvocationHandler<I> {
    fun handleSimple(invocation: SimpleInvocation<I>): Any?
    suspend fun handleSuspend(invocation: SuspendInvocation<I>): Any?

    fun decoratedBy(decorator: InvocationDecorator<I>) = decorator.apply(this)

    companion object {
        fun <I> of(target: I) = object : InvocationHandler<I> {
            override fun handleSimple(invocation: SimpleInvocation<I>): Any? = invocation.apply(target)
            override suspend fun handleSuspend(invocation: SuspendInvocation<I>): Any? = invocation.apply(target)
        }
    }
}
