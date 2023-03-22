package com.soywiz.kmem.pack

import com.soywiz.kmem.*


expect class Int4Pack
expect val Int4Pack.i0: Int
expect val Int4Pack.i1: Int
expect val Int4Pack.i2: Int
expect val Int4Pack.i3: Int
expect fun int4PackOf(i0: Int, i1: Int, i2: Int, i3: Int): Int4Pack
fun Int4Pack.copy(i0: Int = this.i0, i1: Int = this.i1, i2: Int = this.i2, i3: Int = this.i3): Int4Pack = int4PackOf(i0, i1, i2, i3)




expect class Half8Pack
expect val Half8Pack.h0: Float
expect val Half8Pack.h1: Float
expect val Half8Pack.h2: Float
expect val Half8Pack.h3: Float
expect val Half8Pack.h4: Float
expect val Half8Pack.h5: Float
expect val Half8Pack.h6: Float
expect val Half8Pack.h7: Float
expect fun half8PackOf(h0: Float, h1: Float, h2: Float, h3: Float, h4: Float, h5: Float, h6: Float, h7: Float): Half8Pack





inline class Half4Pack private constructor(val data: Short4Pack) {
    constructor(x: Half, y: Half, z: Half, w: Half) : this(short4PackOf(
        x.rawBits.toShort(),
        y.rawBits.toShort(),
        z.rawBits.toShort(),
        w.rawBits.toShort(),
    ))
    val x: Half get() = Half.fromBits(data.s0)
    val y: Half get() = Half.fromBits(data.s1)
    val z: Half get() = Half.fromBits(data.s2)
    val w: Half get() = Half.fromBits(data.s3)
}

expect class Float4Pack
expect val Float4Pack.f0: Float
expect val Float4Pack.f1: Float
expect val Float4Pack.f2: Float
expect val Float4Pack.f3: Float
expect fun float4PackOf(f0: Float, f1: Float, f2: Float, f3: Float): Float4Pack
fun Float4Pack.copy(f0: Float = this.f0, f1: Float = this.f1, f2: Float = this.f2, f3: Float = this.f3): Float4Pack = float4PackOf(f0, f1, f2, f3)






expect class BFloat3Half4Pack
// 21-bit BFloat precision
expect val BFloat3Half4Pack.b0: Float
expect val BFloat3Half4Pack.b1: Float
expect val BFloat3Half4Pack.b2: Float
// 16-bit Half Float precision
expect val BFloat3Half4Pack.hf0: Float
expect val BFloat3Half4Pack.hf1: Float
expect val BFloat3Half4Pack.hf2: Float
expect val BFloat3Half4Pack.hf3: Float
expect fun bfloat3Half4PackOf(b0: Float, b1: Float, b2: Float, hf0: Float, hf1: Float, hf2: Float, hf3: Float): BFloat3Half4Pack






expect class BFloat6Pack
expect val BFloat6Pack.bf0: Float
expect val BFloat6Pack.bf1: Float
expect val BFloat6Pack.bf2: Float
expect val BFloat6Pack.bf3: Float
expect val BFloat6Pack.bf4: Float
expect val BFloat6Pack.bf5: Float
expect fun bfloat6PackOf(bf0: Float, bf1: Float, bf2: Float, bf3: Float, bf4: Float, bf5: Float): BFloat6Pack





expect class Float2Pack
expect val Float2Pack.f0: Float
expect val Float2Pack.f1: Float
expect fun float2PackOf(f0: Float, f1: Float): Float2Pack

expect class Int2Pack
expect val Int2Pack.i0: Int
expect val Int2Pack.i1: Int
expect fun int2PackOf(i0: Int, i1: Int): Int2Pack

expect class Short4Pack
expect val Short4Pack.s0: Short
expect val Short4Pack.s1: Short
expect val Short4Pack.s2: Short
expect val Short4Pack.s3: Short
expect fun short4PackOf(s0: Short, s1: Short, s2: Short, s3: Short): Short4Pack
