package com.vholodynskyi.assignment

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicReference


private object UNINITIALIZED

fun main() = runBlocking {
    numberList.withLatestFrom(getString()) { number, string -> "$number$string " }.collect { value ->
        print(value)
    }
    println(".")
    numberList.combineLatest(getString()) { number, string -> "$number$string " }.collect { value ->
        print(value)
    }
}

val numberList = (1..5).asFlow().onEach { delay(200) }

private fun getString(): Flow<String> = flow {
    listOf("A", "B", "C", "D").forEach {
        delay(
            when (it) {
                "A" -> 300
                "B" -> 200
                "C" -> 100
                "D" -> 100
                else -> 100
            }
        )
        emit(it)
    }
}


fun <A, B, R> Flow<A>.combineLatest(other: Flow<B>, transform: suspend (A, B) -> R): Flow<R> =
    channelFlow {
        coroutineScope {
            val outerScope = this

            val latestA = AtomicReference<A?>(null)
            val latestB = AtomicReference<B?>(null)
            launch {
                try {
                    collect { a: A ->
                        latestA.set(a)
                        latestB.get()?.let { b ->
                            send(transform(a, b))
                        }
                    }
                } catch (e: CancellationException) {
                    outerScope.cancel(e)
                }
            }
            launch {
                try {
                    other.collect { b: B ->
                        latestB.set(b)
                        latestA.get()?.let { a ->
                            send(transform(a, b))
                        }
                    }
                } catch (e: CancellationException) {
                    outerScope.cancel(e)
                }
            }
        }
    }

fun <A, B, R> Flow<A>.withLatestFrom(other: Flow<B>, transform: suspend (A, B) -> R): Flow<R> =
    flow {
        coroutineScope {
            val latestB = AtomicReference<Any>(UNINITIALIZED)
            val outerScope = this

            launch {
                try {
                    other.collect { latestB.set(it) }
                } catch (e: CancellationException) {
                    outerScope.cancel(e) // cancel outer scope on cancellation exception, too
                }
            }

            collect { a: A ->
                latestB.get().let {
                    if (it != UNINITIALIZED) {
                        emit(transform(a, it as B))
                    }
                }
            }
        }
    }