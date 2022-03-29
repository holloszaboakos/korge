import com.soywiz.kds.*
import com.soywiz.klock.*
import com.soywiz.klogger.*
import com.soywiz.kmem.*
import com.soywiz.korag.*
import com.soywiz.korag.shader.*
import com.soywiz.korge.render.*
import com.soywiz.korge.view.*
import com.soywiz.korge.view.BlendMode
import com.soywiz.korim.bitmap.*
import com.soywiz.korim.color.*
import com.soywiz.korim.font.*
import com.soywiz.korim.format.*
import com.soywiz.korim.paint.*
import com.soywiz.korim.text.*
import com.soywiz.korim.vector.*
import com.soywiz.korim.vector.format.*
import com.soywiz.korio.file.std.*
import com.soywiz.korma.geom.*
import com.soywiz.korma.geom.shape.*
import com.soywiz.korma.geom.vector.*

suspend fun Stage.mainGpuVectorRendering() {
    Console.log("[1]")
    val korgeBitmap = resourcesVfs["korge.png"].readBitmap()
    Console.log("[2]")
    val tigerSvg = measureTime({ resourcesVfs["Ghostscript_Tiger.svg"].readSVG() }) {
        println("Elapsed $it")
    }
    Console.log("[3]")
    //AudioData(44100, AudioSamples(1, 1024)).toSound().play()

    val PAINT_TIGER = true
    val PAINT_SHAPES = true
    val PAINT_BITMAP = true
    val PAINT_TEXT = true
    val PAINT_LINEAR_GRADIENT = true
    val PAINT_RADIAL_GRADIENT = true

    fun Context2d.buildGraphics(kind: String) {
        if (PAINT_TIGER) {
            keep {
                scale(0.5)
                draw(tigerSvg)
            }
        }
        if (PAINT_SHAPES) {
            keep {
                translate(100, 200)
                fill(Colors.BLUE) {
                    rect(-10, -10, 120, 120)
                    rectHole(40, 40, 80, 80)
                }
                fill(Colors.YELLOW) {
                    this.circle(100, 100, 40)
                    //rect(-100, -100, 500, 500)
                    //rectHole(40, 40, 320, 320)
                }
                fill(Colors.RED) {
                    regularPolygon(6, 30.0, x = 100.0, y = 100.0)
                    //rect(-100, -100, 500, 500)
                    //rectHole(40, 40, 320, 320)
                }
            }
        }
        keep {
            translate(100, 20)
            scale(2.0)
            if (PAINT_BITMAP) {
                globalAlpha = 0.75
                fillStyle = BitmapPaint(
                    korgeBitmap,
                    Matrix().translate(50, 50).scale(0.125),
                    cycleX = CycleMethod.REPEAT,
                    cycleY = CycleMethod.REPEAT
                )
                fillRect(0.0, 0.0, 100.0, 100.0)
            }

            if (PAINT_LINEAR_GRADIENT) {
                globalAlpha = 0.9
                fillStyle =
                    //createLinearGradient(150.0, 0.0, 200.0, 50.0)
                    createLinearGradient(0.0, 0.0, 100.0, 100.0, transform = Matrix().scale(0.5).pretranslate(300, 0))
                        //.addColorStop(0.0, Colors.BLACK).addColorStop(1.0, Colors.WHITE)
                        .addColorStop(0.0, Colors.RED).addColorStop(0.5, Colors.GREEN).addColorStop(1.0, Colors.BLUE)
                fillRect(100.0, 0.0, 100.0, 100.0)
            }
            if (PAINT_RADIAL_GRADIENT) {
                globalAlpha = 0.9
                fillStyle =
                    //createLinearGradient(150.0, 0.0, 200.0, 50.0)
                    createRadialGradient(150,150,30, 130,180,70)
                    //createLinearGradient(0.0, 0.0, 100.0, 100.0, transform = Matrix().scale(0.5).pretranslate(300, 0))
                        //.addColorStop(0.0, Colors.BLACK).addColorStop(1.0, Colors.WHITE)
                        .addColorStop(0.0, Colors.RED).addColorStop(0.5, Colors.GREEN).addColorStop(1.0, Colors.BLUE)
                fillRect(100.0, 100.0, 100.0, 100.0)
            }
        }
        if (PAINT_TEXT) {
            keep {
                font = DefaultTtfFont
                fontSize = 16.0
                fillStyle = Colors.WHITE
                alignment = TextAlignment.TOP_LEFT
                fillText("HELLO WORLD ($kind)", 0.0, 16.0)
            }
        }
    }

    buildShape { buildGraphics("only shape") }

    measureTime({
        buildShape { buildGraphics("only shape") }
    }) {
        println("BUILD SHAPE: $it")
    }

    measureTime({
        gpuShapeView { buildGraphics("GPU") }.xy(0, 0)//.rotation(45.degrees)
    }) {
        println("GPU SHAPE: $it")
    }
    measureTime({
        image(NativeImage(512, 512).context2d { buildGraphics("NATIVE") }).xy(550, 0)
    }) {
        println("CONTEXT2D NATIVE: $it")
    }
    measureTime({
        image(Bitmap32(512, 512).context2d { buildGraphics("KOTLIN") }).xy(550, 370)
    }) {
        println("CONTEXT2D BITMAP: $it")
    }
}

inline fun Container.gpuShapeView(buildContext2d: Context2d.() -> Unit) =
    GpuShapeView(buildShape { buildContext2d() }).addTo(this)

inline fun Container.gpuShapeView(shape: Shape, callback: @ViewDslMarker GpuShapeView.() -> Unit = {}) =
    GpuShapeView(shape).addTo(this, callback)

class GpuShapeView(shape: Shape) : View() {
    private val pointCache = FastIdentityMap<VectorPath, PointArrayList>()

    var shape: Shape = shape
        set(value) {
            field = value
            pointCache.clear()
        }

    companion object {
        val u_Color = Uniform("u_Color", VarType.Float4)
        val u_GlobalAlpha = Uniform("u_GlobalAlpha", VarType.Float1)
        val u_Transform = Uniform("u_Transform", VarType.Mat4)
        val u_Gradientp0 = Uniform("u_Gradientp0", VarType.Float3)
        val u_Gradientp1 = Uniform("u_Gradientp1", VarType.Float3)
        val LAYOUT = VertexLayout(DefaultShaders.a_Pos)
        val LAYOUT_FILL = VertexLayout(DefaultShaders.a_Pos, DefaultShaders.a_Tex)
        val VERTEX_FILL = VertexShaderDefault {
            SET(out, (u_ProjMat * u_ViewMat) * vec4(a_Pos, 0f.lit, 1f.lit))
            //SET(out, vec4(a_Pos, 0f.lit, 1f.lit))
            SET(v_Tex, DefaultShaders.a_Tex)
        }
        val PROGRAM_STENCIL = Program(
            vertex = VertexShaderDefault { SET(out, (u_ProjMat * u_ViewMat) * vec4(a_Pos, 0f.lit, 1f.lit)) },
            fragment = FragmentShaderDefault { SET(out, vec4(1f.lit, 0f.lit, 0f.lit, 1f.lit)) },
        )

        fun Program.Builder.UPDATE_GLOBAL_ALPHA() {
            SET(out.a, out.a * u_GlobalAlpha)
        }
        val Operand.pow2: Operand get() = Program.ExpressionBuilder { pow(this@pow2, 2f.lit) }


        val PROGRAM_COLOR = Program(
            vertex = VERTEX_FILL,
            fragment = FragmentShaderDefault {
                SET(out, u_Color)
                UPDATE_GLOBAL_ALPHA()
            },
        )
        val PROGRAM_BITMAP = Program(
            vertex = VERTEX_FILL,
            fragment = FragmentShaderDefault {
                // @TODO: we should convert 0..1 to texture slice coordinates
                SET(out, texture2D(u_Tex, fract(vec2((u_Transform * vec4(v_Tex, 0f.lit, 1f.lit))["xy"]))))
                UPDATE_GLOBAL_ALPHA()
                //SET(out, vec4(1f.lit, 1f.lit, 0f.lit, 1f.lit))
            },
        )
        val PROGRAM_LINEAR_GRADIENT = Program(
            vertex = VERTEX_FILL,
            fragment = FragmentShaderDefault {
                SET(out, texture2D(u_Tex, (u_Transform * vec4(v_Tex.x, v_Tex.y, 0f.lit, 1f.lit))["xy"]))
                UPDATE_GLOBAL_ALPHA()
            },
        )
        val PROGRAM_RADIAL_GRADIENT = Program(
            vertex = VERTEX_FILL,
            fragment = FragmentShaderDefault {
                val rpoint = createTemp(VarType.Float2)
                SET(rpoint["xy"], (u_Transform * vec4(v_Tex.x, v_Tex.y, 0f.lit, 1f.lit))["xy"])
                val x = rpoint.x
                val y = rpoint.y
                val x0 = u_Gradientp0.x
                val y0 = u_Gradientp0.y
                val r0 = u_Gradientp0.z
                val x1 = u_Gradientp1.x
                val y1 = u_Gradientp1.y
                val r1 = u_Gradientp1.z
                val ratio = t_Temp0.x
                val r0r1_2 = t_Temp0.y
                val r0pow2 = t_Temp0.z
                val r1pow2 = t_Temp0.w
                val y0_y1 = t_Temp1.x
                val x0_x1 = t_Temp1.y
                val r0_r1 = t_Temp1.z
                val radial_scale = t_Temp1.w

                SET(r0r1_2, 2f.lit * r0 * r1)
                SET(r0pow2, r0.pow2)
                SET(r1pow2, r1.pow2)
                SET(x0_x1, x0 - x1)
                SET(y0_y1, y0 - y1)
                SET(r0_r1, r0 - r1)
                SET(radial_scale, 1f.lit / ((r0 - r1).pow2 - (x0 - x1).pow2 - (y0 - y1).pow2))

                SET(ratio, 1f.lit - (-r1 * r0_r1 + x0_x1 * (x1 - x) + y0_y1 * (y1 - y) - sqrt(r1pow2 * ((x0 - x).pow2 + (y0 - y).pow2) - r0r1_2 * ((x0 - x) * (x1 - x) + (y0 - y) * (y1 - y)) + r0pow2 * ((x1 - x).pow2 + (y1 - y).pow2) - (x1 * y0 - x * y0 - x0 * y1 + x * y1 + x0 * y - x1 * y).pow2)) * radial_scale)
                SET(out, texture2D(u_Tex, vec2(ratio, 0f.lit)))
                UPDATE_GLOBAL_ALPHA()
            },
        )
    }

    private val bb = BoundsBuilder()
    override fun getLocalBoundsInternal(out: Rectangle) {
        shape.getBounds(out, bb)
    }

    var msaaSamples: Int = 4

    var bufferWidth = 1000
    var bufferHeight = 1000

    override fun renderInternal(ctx: RenderContext) {
        ctx.flush()
        val currentRenderBuffer = ctx.ag.currentRenderBufferOrMain
        //ctx.renderToTexture(currentRenderBuffer.width, currentRenderBuffer.height, {
        bufferWidth = currentRenderBuffer.width ; bufferHeight = currentRenderBuffer.height
        val time = measureTime {
            ctx.renderToTexture(bufferWidth, bufferHeight, {
                renderShape(ctx, shape)
            }, hasStencil = true, msamples = msaaSamples) { texture ->
                ctx.useBatcher {
                    it.drawQuad(texture, x = 0f, y = 0f)
                }
            }
        }
        //println("GPU RENDER IN: $time")
    }

    private fun renderShape(ctx: RenderContext, shape: Shape) {
        when (shape) {
            EmptyShape -> Unit
            is FillShape -> renderShape(ctx, shape)
            is CompoundShape -> for (v in shape.components) renderShape(ctx, v)
            is PolylineShape -> {
                //println("TODO: PolylineShape not implemented. Convert into fills")
            }
            is TextShape -> {
                renderShape(ctx, shape.primitiveShapes)
            }
            else -> TODO("shape=$shape")
        }
    }

    private fun renderShape(ctx: RenderContext, shape: FillShape) {
        val path = shape.path
        val m = globalMatrix
        //val m = localMatrix
        //val stage = stage!!
        //val stageMatrix = stage.localMatrix
        //println("stage=$stage, globalMatrix=$stageMatrix")

        val points = pointCache.getOrPut(path) {
            val points = PointArrayList()
            path.emitPoints2 { x, y, move ->
                points.add(x, y)
            }
            points
        }

        val bb = BoundsBuilder()
        bb.reset()

        val data = FloatArray(points.size * 2 + 4)
        for (n in 0 until points.size + 1) {
            val x = points.getX(n % points.size).toFloat()
            val y = points.getY(n % points.size).toFloat()
            val tx = m.transformXf(x, y)
            val ty = m.transformYf(x, y)
            data[(n + 1) * 2 + 0] = tx
            data[(n + 1) * 2 + 1] = ty
            bb.add(tx, ty)
        }
        data[0] = ((bb.xmax + bb.xmin) / 2).toFloat()
        data[1] = ((bb.ymax + bb.ymin) / 2).toFloat()

        if (shape.path.winding != Winding.EVEN_ODD) {
            error("Currently only supported EVEN_ODD winding")
        }

        val bounds = bb.getBounds()

        val scissor: AG.Scissor? = AG.Scissor().setTo(Rectangle.fromBounds(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toIntCeil(), bounds.bottom.toIntCeil()))
        //val scissor: AG.Scissor? = null

        ctx.dynamicVertexBufferPool { vertices ->
            vertices.upload(data)
            ctx.batch.updateStandardUniforms()

            ctx.batch.simulateBatchStats(points.size + 2)

            ctx.ag.clearStencil(0, scissor = scissor)
            //ctx.ag.clearStencil(0, scissor = null)
            ctx.ag.draw(
                vertices = vertices,
                program = PROGRAM_STENCIL,
                type = AG.DrawType.TRIANGLE_FAN,
                vertexLayout = LAYOUT,
                vertexCount = points.size + 2,
                uniforms = ctx.batch.uniforms,
                stencil = AG.StencilState(
                    enabled = true,
                    readMask = 0xFF,
                    compareMode = AG.CompareMode.ALWAYS,
                    referenceValue = 0xFF,
                    writeMask = 0xFF,
                    actionOnDepthFail = AG.StencilOp.KEEP,
                    actionOnDepthPassStencilFail = AG.StencilOp.KEEP,
                    actionOnBothPass = AG.StencilOp.INVERT,
                ),
                blending = BlendMode.NONE.factors,
                colorMask = AG.ColorMaskState(false, false, false, false),
                scissor = scissor,
            )
        }
        renderFill(ctx, shape.paint, shape.transform, scissor, shape.globalAlpha)
    }

    private val colorUniforms = AG.UniformValues()
    private val bitmapUniforms = AG.UniformValues()
    private val gradientUniforms = AG.UniformValues()
    private val gradientBitmap = Bitmap32(256, 1)

    private val colorF = FloatArray(4)

    private fun renderFill(
        ctx: RenderContext,
        paint: Paint,
        stateTransform: Matrix,
        scissor: AG.Scissor?,
        globalAlpha: Double,
    ) {
        if (paint is NonePaint) return

        ctx.dynamicVertexBufferPool { vertices ->
            val data = FloatArray(4 * 4)
            var n = 0

            val x0 = 0f
            val y0 = 0f
            val x1 = bufferWidth.toFloat()
            val y1 = bufferHeight.toFloat()

            val vm = Matrix()
            vm.copyFrom(stage!!.globalMatrixInv)

            val l0 = vm.transform(0f, 0f)
            val l1 = vm.transform(bufferWidth.toFloat(), bufferHeight.toFloat())
            val lx0 = l0.xf
            val ly0 = l0.yf
            val lx1 = l1.xf
            val ly1 = l1.yf

            data[n++] = x0; data[n++] = y0; data[n++] = lx0; data[n++] = ly0
            data[n++] = x1; data[n++] = y0; data[n++] = lx1; data[n++] = ly0
            data[n++] = x1; data[n++] = y1; data[n++] = lx1; data[n++] = ly1
            data[n++] = x0; data[n++] = y1; data[n++] = lx0; data[n++] = ly1

            //println("[($lx0,$ly0)-($lx1,$ly1)]")

            vertices.upload(data)
            ctx.useBatcher { batch ->
                batch.updateStandardUniforms()
                var uniforms: AG.UniformValues = colorUniforms
                var program: Program = PROGRAM_COLOR
                when (paint) {
                    is NonePaint -> {
                        return
                    }
                    is ColorPaint -> {
                        val color = paint
                        color.writeFloat(colorF)
                        colorUniforms[u_Color] = colorF
                        program = PROGRAM_COLOR
                        uniforms = colorUniforms
                    }
                    is BitmapPaint -> {
                        val mat = Matrix().apply {
                            identity()
                            preconcat(paint.transform)
                            preconcat(stateTransform)
                            invert()
                            scale(1.0 / paint.bitmap.width, 1.0 / paint.bitmap.height)
                        }

                        //val mat = (paint.transform * stateTransform)
                        //mat.scale(1.0 / paint.bitmap.width, 1.0 / paint.bitmap.height)
                        //println("mat=$mat")
                        bitmapUniforms[DefaultShaders.u_Tex] = AG.TextureUnit(ctx.getTex(paint.bitmap).base)
                        bitmapUniforms[u_Transform] = mat.toMatrix3D() // @TODO: Why is this transposed???
                        program = PROGRAM_BITMAP
                        uniforms = bitmapUniforms
                    }
                    is GradientPaint -> {
                        paint.fillColors(gradientBitmap.dataPremult)
                        val npaint = paint.copy(transform = Matrix().apply {
                            identity()
                            preconcat(paint.transform)
                            preconcat(stateTransform)
                        })
                        //val mat = stateTransform * paint.gradientMatrix
                        val mat = when (paint.kind) {
                            GradientKind.RADIAL -> npaint.transform.inverted()
                            else -> npaint.gradientMatrix
                        }
                        gradientUniforms[DefaultShaders.u_Tex] = AG.TextureUnit(ctx.getTex(gradientBitmap).base)
                        gradientUniforms[u_Transform] = mat.toMatrix3D() // @TODO: Why is this transposed???
                        gradientUniforms[u_Gradientp0] = floatArrayOf(paint.x0.toFloat(), paint.y0.toFloat(), paint.r0.toFloat())
                        gradientUniforms[u_Gradientp1] = floatArrayOf(paint.x1.toFloat(), paint.y1.toFloat(), paint.r1.toFloat())
                        program = when (paint.kind) {
                            GradientKind.RADIAL -> PROGRAM_RADIAL_GRADIENT
                            else -> PROGRAM_LINEAR_GRADIENT
                        }
                        uniforms = gradientUniforms
                    }
                    else -> {
                        TODO("paint=$paint")
                    }
                }
                uniforms[u_GlobalAlpha] = globalAlpha.toFloat()
                batch.setTemporalUniforms(uniforms) {
                    ctx.batch.simulateBatchStats(4)
                    //println("ctx.batch.uniforms=${ctx.batch.uniforms}")
                    ctx.ag.draw(
                        vertices = vertices,
                        program = program,
                        type = AG.DrawType.TRIANGLE_FAN,
                        vertexLayout = LAYOUT_FILL,
                        vertexCount = 4,
                        uniforms = ctx.batch.uniforms,
                        stencil = AG.StencilState(
                            enabled = true,
                            compareMode = AG.CompareMode.NOT_EQUAL,
                            writeMask = 0,
                        ),
                        blending = BlendMode.NORMAL.factors,
                        colorMask = AG.ColorMaskState(true, true, true, true),
                        scissor = scissor,
                    )
                }
            }
        }
    }
}
