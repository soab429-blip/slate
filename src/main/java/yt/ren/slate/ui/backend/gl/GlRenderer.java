package yt.ren.slate.ui.backend.gl;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import yt.ren.slate.ui.backend.RecordingRenderer;
import yt.ren.slate.ui.vector.VectorContext;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public class GlRenderer extends RecordingRenderer {
    private static final String SHADER_HEADER = "#version 120\n#define UNIFORMARRAY_SIZE 11\n\n";
    private static final String VERTEX_SHADER = "#ifdef NANOVG_GL3\n\tuniform vec2 viewSize;\n\tin vec2 vertex;\n\tin vec2 tcoord;\n\tout vec2 ftcoord;\n\tout vec2 fpos;\n#else\n\tuniform vec2 viewSize;\n\tattribute vec2 vertex;\n\tattribute vec2 tcoord;\n\tvarying vec2 ftcoord;\n\tvarying vec2 fpos;\n#endif\nvoid main(void) {\n\tftcoord = tcoord;\n\tfpos = vertex;\n\tgl_Position = vec4(2.0*vertex.x/viewSize.x - 1.0, 1.0 - 2.0*vertex.y/viewSize.y, 0, 1);\n}\n";
    private static final String FRAGMENT_SHADER = "#ifdef GL_ES\n#if defined(GL_FRAGMENT_PRECISION_HIGH) || defined(NANOVG_GL3)\n precision highp float;\n#else\n precision mediump float;\n#endif\n#endif\n#ifdef NANOVG_GL3\n#ifdef USE_UNIFORMBUFFER\n\tlayout(std140) uniform frag {\n\t\tmat3 scissorMat;\n\t\tmat3 paintMat;\n\t\tvec4 innerCol;\n\t\tvec4 outerCol;\n\t\tvec2 scissorExt;\n\t\tvec2 scissorScale;\n\t\tvec2 extent;\n\t\tfloat radius;\n\t\tfloat feather;\n\t\tfloat strokeMult;\n\t\tfloat strokeThr;\n\t\tint texType;\n\t\tint type;\n\t};\n#else\n\tuniform vec4 frag[UNIFORMARRAY_SIZE];\n#endif\n\tuniform sampler2D tex;\n\tin vec2 ftcoord;\n\tin vec2 fpos;\n\tout vec4 outColor;\n#else\n\tuniform vec4 frag[UNIFORMARRAY_SIZE];\n\tuniform sampler2D tex;\n\tvarying vec2 ftcoord;\n\tvarying vec2 fpos;\n#endif\n#ifndef USE_UNIFORMBUFFER\n\t#define scissorMat mat3(frag[0].xyz, frag[1].xyz, frag[2].xyz)\n\t#define paintMat mat3(frag[3].xyz, frag[4].xyz, frag[5].xyz)\n\t#define innerCol frag[6]\n\t#define outerCol frag[7]\n\t#define scissorExt frag[8].xy\n\t#define scissorScale frag[8].zw\n\t#define extent frag[9].xy\n\t#define radius frag[9].z\n\t#define feather frag[9].w\n\t#define strokeMult frag[10].x\n\t#define strokeThr frag[10].y\n\t#define texType int(frag[10].z)\n\t#define type int(frag[10].w)\n#endif\n\nfloat sdroundrect(vec2 pt, vec2 ext, float rad) {\n\tvec2 ext2 = ext - vec2(rad,rad);\n\tvec2 d = abs(pt) - ext2;\n\treturn min(max(d.x,d.y),0.0) + length(max(d,0.0)) - rad;\n}\n\nfloat scissorMask(vec2 p) {\n\tvec2 sc = (abs((scissorMat * vec3(p,1.0)).xy) - scissorExt);\n\tsc = vec2(0.5,0.5) - sc * scissorScale;\n\treturn clamp(sc.x,0.0,1.0) * clamp(sc.y,0.0,1.0);\n}\n#ifdef EDGE_AA\nfloat strokeMask() {\n\treturn min(1.0, (1.0-abs(ftcoord.x*2.0-1.0))*strokeMult) * min(1.0, ftcoord.y);\n}\n#endif\n\nvoid main(void) {\n   vec4 result;\n\tfloat scissor = scissorMask(fpos);\n#ifdef EDGE_AA\n\tfloat strokeAlpha = strokeMask();\n\tif (strokeAlpha < strokeThr) discard;\n#else\n\tfloat strokeAlpha = 1.0;\n#endif\n\tif (type == 0) {\n\t\tvec2 pt = (paintMat * vec3(fpos,1.0)).xy;\n\t\tfloat d = clamp((sdroundrect(pt, extent, radius) + feather*0.5) / feather, 0.0, 1.0);\n\t\tvec4 color = mix(innerCol,outerCol,d);\n\t\tcolor *= strokeAlpha * scissor;\n\t\tresult = color;\n\t} else if (type == 1) {\n\t\tvec2 pt = (paintMat * vec3(fpos,1.0)).xy / extent;\n#ifdef NANOVG_GL3\n\t\tvec4 color = texture(tex, pt);\n#else\n\t\tvec4 color = texture2D(tex, pt);\n#endif\n\t\tif (texType == 1) color = vec4(color.xyz*color.w,color.w);\t\tif (texType == 2) color = vec4(color.x);\n\t\tcolor *= innerCol;\n\t\tcolor *= strokeAlpha * scissor;\n\t\tresult = color;\n\t} else if (type == 2) {\n\t\tresult = vec4(1,1,1,1);\n\t} else if (type == 3) {\n#ifdef NANOVG_GL3\n\t\tvec4 color = texture(tex, ftcoord);\n#else\n\t\tvec4 color = texture2D(tex, ftcoord);\n#endif\n\t\tif (texType == 1) color = vec4(color.xyz*color.w,color.w);\t\tif (texType == 2) color = vec4(color.x);\t\tcolor *= scissor;\n\t\tresult = color * innerCol;\n\t}\n#ifdef NANOVG_GL3\n\toutColor = result;\n#else\n\tgl_FragColor = result;\n#endif\n}\n";

    int program;
    int fragmentShader;
    int vertexShader;
    int viewLocation;
    int textureLocation;
    int fragmentLocation;
    private int vertexBuffer;
    private int boundTexture;
    private int stencilWriteMask;
    private int stencilFunction;
    private int stencilReference;
    private int stencilFunctionMask;
    private final int[] blend = new int[4];
    private int dummyTexture;
    private FloatBuffer vertexUpload = BufferUtils.createFloatBuffer(4);
    private final FloatBuffer uniformUpload = BufferUtils.createFloatBuffer(UNIFORM_FLOATS);
    private final FloatBuffer viewUpload = BufferUtils.createFloatBuffer(2);

    private GlRenderer(int flags) {
        super(flags);
    }

    public static VectorContext createVector(int flags) {
        return VectorContext.create(new GlRenderer(flags));
    }

    private void error(String stage) {
        if ((flags & 4) != 0) {
            int code = GL11.glGetError();
            if (code != 0) {
                System.out.printf("Error %08x after %s\n", code, stage);
            }
        }
    }

    private void bindTexture(int texture) {
        if (boundTexture != texture) {
            boundTexture = texture;
            GL11.glBindTexture(0xde1, texture);
        }
    }

    private boolean createShader(String options) {
        program = 0;
        fragmentShader = 0;
        vertexShader = 0;
        viewLocation = 0;
        textureLocation = 0;
        fragmentLocation = 0;
        int newProgram = GL20.glCreateProgram();
        int vertex = GL20.glCreateShader(0x8b31);
        int fragment = GL20.glCreateShader(0x8b30);
        String prefix = options != null ? options : "";
        GL20.glShaderSource(vertex, new CharSequence[]{SHADER_HEADER, prefix, VERTEX_SHADER});
        GL20.glShaderSource(fragment, new CharSequence[]{SHADER_HEADER, prefix, FRAGMENT_SHADER});
        GL20.glCompileShader(vertex);
        int status = GL20.glGetShaderi(vertex, 0x8b81);
        int failedShader = vertex;
        String failedStage = "vert";
        if (status == 1) {
            GL20.glCompileShader(fragment);
            status = GL20.glGetShaderi(fragment, 0x8b81);
            failedShader = fragment;
            failedStage = "frag";
            if (status == 1) {
                GL20.glAttachShader(newProgram, vertex);
                GL20.glAttachShader(newProgram, fragment);
                GL20.glBindAttribLocation(newProgram, 0, "vertex");
                GL20.glBindAttribLocation(newProgram, 1, "tcoord");
                GL20.glLinkProgram(newProgram);
                status = GL20.glGetProgrami(newProgram, 0x8b82);
                if (status == 1) {
                    program = newProgram;
                    vertexShader = vertex;
                    fragmentShader = fragment;
                    return true;
                }
                String log = GL20.glGetProgramInfoLog(newProgram, 512);
                System.out.printf("Program %s error:\n%s\n", "shader", log);
                return false;
            }
        }
        String log = GL20.glGetShaderInfoLog(failedShader, 512);
        System.out.printf("Shader %s/%s error:\n%s\n", "shader", failedStage, log);
        return false;
    }

    @Override
    public int create() {
        error("init");
        if (!createShader((flags & 1) != 0 ? "#define EDGE_AA 1\n" : null)) {
            return 0;
        }
        error("uniform locations");
        viewLocation = GL20.glGetUniformLocation(program, "viewSize");
        textureLocation = GL20.glGetUniformLocation(program, "tex");
        fragmentLocation = GL20.glGetUniformLocation(program, "frag");
        vertexBuffer = GL15.glGenBuffers();
        dummyTexture = createTexture(1, 1, 1, 0, null);
        error("create done");
        GL11.glFinish();
        return 1;
    }

    @Override
    protected void uploadTexture(Texture texture, ByteBuffer data) {
        texture.handle = GL11.glGenTextures();
        bindTexture((int) texture.handle);
        GL11.glPixelStorei(0xcf5, 1);
        GL11.glPixelStorei(0xcf2, texture.width);
        GL11.glPixelStorei(0xcf4, 0);
        GL11.glPixelStorei(0xcf3, 0);
        int format = texture.type == TEXTURE_RGBA ? 0x1908 : GlCompat.alphaFormat();
        GL11.glTexImage2D(0xde1, 0, format, texture.width, texture.height, 0, format, 0x1401, data);
        boolean mipmap = (texture.flags & IMAGE_GENERATE_MIPMAPS) != 0;
        boolean linear = (texture.flags & IMAGE_NEAREST) == 0;
        GL11.glTexParameteri(0xde1, 0x2801, linear ? (mipmap ? 0x2703 : 0x2601) : (mipmap ? 0x2700 : 0x2600));
        GL11.glTexParameteri(0xde1, 0x2800, linear ? 0x2601 : 0x2600);
        GL11.glTexParameteri(0xde1, 0x2802, (texture.flags & IMAGE_REPEAT_X) != 0 ? 0x2901 : 0x812f);
        GL11.glTexParameteri(0xde1, 0x2803, (texture.flags & IMAGE_REPEAT_Y) != 0 ? 0x2901 : 0x812f);
        GL11.glPixelStorei(0xcf5, 4);
        GL11.glPixelStorei(0xcf2, 0);
        GL11.glPixelStorei(0xcf4, 0);
        GL11.glPixelStorei(0xcf3, 0);
        if (mipmap) {
            GlCompat.generateMipmap(0xde1);
        }
        error("create tex");
        bindTexture(0);
    }

    @Override
    protected void updateTexture(Texture texture, int x, int y, int width, int height, ByteBuffer data) {
        bindTexture((int) texture.handle);
        GL11.glPixelStorei(0xcf5, 1);
        GL11.glPixelStorei(0xcf2, texture.width);
        GL11.glPixelStorei(0xcf4, x);
        GL11.glPixelStorei(0xcf3, y);
        int format = texture.type == TEXTURE_RGBA ? 0x1908 : GlCompat.alphaFormat();
        GL11.glTexSubImage2D(0xde1, 0, x, y, width, height, format, 0x1401, data);
        GL11.glPixelStorei(0xcf5, 4);
        GL11.glPixelStorei(0xcf2, 0);
        GL11.glPixelStorei(0xcf4, 0);
        GL11.glPixelStorei(0xcf3, 0);
        bindTexture(0);
    }

    @Override
    protected void releaseTexture(Texture texture) {
        if (texture.handle != 0) {
            GL11.glDeleteTextures((int) texture.handle);
        }
    }

    @Override
    public void destroy() {
        if (program != 0) {
            GL20.glDeleteProgram(program);
        }
        if (vertexShader != 0) {
            GL20.glDeleteShader(vertexShader);
        }
        if (fragmentShader != 0) {
            GL20.glDeleteShader(fragmentShader);
        }
        if (vertexBuffer != 0) {
            GL15.glDeleteBuffers(vertexBuffer);
        }
        releaseAllTextures();
    }

    private void setUniforms(int offset, int image) {
        uniformUpload.clear();
        uniformUpload.put(uniforms, offset / 4, UNIFORM_FLOATS);
        uniformUpload.flip();
        GL20.glUniform4(fragmentLocation, uniformUpload);
        Texture texture = image != 0 ? findTexture(image) : null;
        if (texture == null) {
            texture = findTexture(dummyTexture);
        }
        bindTexture(texture != null ? (int) texture.handle : 0);
        error("tex paint tex");
    }

    private void stencilMask(int mask) {
        if (stencilWriteMask != mask) {
            stencilWriteMask = mask;
            GL11.glStencilMask(mask);
        }
    }

    private void stencilFunction(int function, int reference, int mask) {
        if (stencilFunction != function || stencilReference != reference || stencilFunctionMask != mask) {
            stencilFunction = function;
            stencilReference = reference;
            stencilFunctionMask = mask;
            GL11.glStencilFunc(function, reference, mask);
        }
    }

    private void blendFunction(int[] value) {
        if (blend[0] != value[0] || blend[1] != value[1] || blend[2] != value[2] || blend[3] != value[3]) {
            System.arraycopy(value, 0, blend, 0, 4);
            GL14.glBlendFuncSeparate(value[0], value[1], value[2], value[3]);
        }
    }

    private void strokePaths(int offset, int count) {
        for (int i = 0; i < count; ++i) {
            GL11.glDrawArrays(5, paths[(offset + i) * 4 + 2], paths[(offset + i) * 4 + 3]);
        }
    }

    private void fillCall(Call call) {
        int offset = call.pathOffset;
        int count = call.pathCount;
        GL11.glEnable(0xb90);
        stencilMask(0xff);
        stencilFunction(0x207, 0, 0xff);
        GL11.glColorMask(false, false, false, false);
        setUniforms(call.uniformOffset, 0);
        error("fill simple");
        GL20.glStencilOpSeparate(0x404, 0x1e00, 0x1e00, 0x8507);
        GL20.glStencilOpSeparate(0x405, 0x1e00, 0x1e00, 0x8508);
        GL11.glDisable(0xb44);
        for (int i = 0; i < count; ++i) {
            GL11.glDrawArrays(6, paths[(offset + i) * 4], paths[(offset + i) * 4 + 1]);
        }
        GL11.glEnable(0xb44);
        GL11.glColorMask(true, true, true, true);
        setUniforms(UNIFORM_STRIDE + call.uniformOffset, call.image);
        error("fill fill");
        if ((flags & 1) != 0) {
            stencilFunction(0x202, 0, 0xff);
            GL11.glStencilOp(0x1e00, 0x1e00, 0x1e00);
            strokePaths(offset, count);
        }
        stencilFunction(0x205, 0, 0xff);
        GL11.glStencilOp(0, 0, 0);
        GL11.glDrawArrays(5, call.triangleOffset, call.triangleCount);
        GL11.glDisable(0xb90);
    }

    private void convexFill(Call call) {
        int offset = call.pathOffset;
        int count = call.pathCount;
        setUniforms(call.uniformOffset, call.image);
        error("convex fill");
        for (int i = 0; i < count; ++i) {
            int p = (offset + i) * 4;
            GL11.glDrawArrays(6, paths[p], paths[p + 1]);
            if (paths[p + 3] > 0) {
                GL11.glDrawArrays(5, paths[p + 2], paths[p + 3]);
            }
        }
    }

    private void strokeCall(Call call) {
        int offset = call.pathOffset;
        int count = call.pathCount;
        if ((flags & 2) != 0) {
            GL11.glEnable(0xb90);
            stencilMask(0xff);
            stencilFunction(0x202, 0, 0xff);
            GL11.glStencilOp(0x1e00, 0x1e00, 0x1e02);
            setUniforms(UNIFORM_STRIDE + call.uniformOffset, call.image);
            error("stroke fill 0");
            strokePaths(offset, count);
            setUniforms(call.uniformOffset, call.image);
            stencilFunction(0x202, 0, 0xff);
            GL11.glStencilOp(0x1e00, 0x1e00, 0x1e00);
            strokePaths(offset, count);
            GL11.glColorMask(false, false, false, false);
            stencilFunction(0x207, 0, 0xff);
            GL11.glStencilOp(0, 0, 0);
            error("stroke fill 1");
            strokePaths(offset, count);
            GL11.glColorMask(true, true, true, true);
            GL11.glDisable(0xb90);
        } else {
            setUniforms(call.uniformOffset, call.image);
            error("stroke fill");
            strokePaths(offset, count);
        }
    }

    @Override
    public void flush() {
        if (callCount > 0) {
            GL20.glUseProgram(program);
            GL11.glEnable(0xb44);
            GL11.glCullFace(0x405);
            GL11.glFrontFace(0x901);
            GL11.glEnable(0xbe2);
            GL11.glDisable(0xb71);
            GL11.glDisable(0xc11);
            GL11.glColorMask(true, true, true, true);
            GL11.glStencilMask(0xffffffff);
            GL11.glStencilOp(0x1e00, 0x1e00, 0x1e00);
            GL11.glStencilFunc(0x207, 0, 0xffffffff);
            GL13.glActiveTexture(0x84c0);
            GL11.glBindTexture(0xde1, 0);
            boundTexture = 0;
            stencilWriteMask = 0xffffffff;
            stencilFunction = 0x207;
            stencilReference = 0;
            stencilFunctionMask = 0xffffffff;
            blend[0] = 0x500;
            blend[1] = 0x500;
            blend[2] = 0x500;
            blend[3] = 0x500;
            GL15.glBindBuffer(0x8892, vertexBuffer);
            int floats = vertexCount * 4;
            if (vertexUpload.capacity() < floats) {
                vertexUpload = BufferUtils.createFloatBuffer(floats);
            }
            vertexUpload.clear();
            vertexUpload.put(vertices, 0, floats);
            vertexUpload.flip();
            GL15.glBufferData(0x8892, vertexUpload, 0x88e0);
            GL20.glEnableVertexAttribArray(0);
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(0, 2, 0x1406, false, 0x10, 0L);
            GL20.glVertexAttribPointer(1, 2, 0x1406, false, 0x10, 8L);
            GL20.glUniform1i(textureLocation, 0);
            viewUpload.clear();
            viewUpload.put(view);
            viewUpload.flip();
            GL20.glUniform2(viewLocation, viewUpload);
            for (int i = 0; i < callCount; ++i) {
                Call call = calls[i];
                blendFunction(call.blend);
                switch (call.type) {
                    case 1:
                        fillCall(call);
                        break;
                    case 2:
                        convexFill(call);
                        break;
                    case 3:
                        strokeCall(call);
                        break;
                    case 4:
                        setUniforms(call.uniformOffset, call.image);
                        error("triangles fill");
                        GL11.glDrawArrays(4, call.triangleOffset, call.triangleCount);
                        break;
                    default:
                        break;
                }
            }
            GL20.glDisableVertexAttribArray(0);
            GL20.glDisableVertexAttribArray(1);
            GL11.glDisable(0xb44);
            GL15.glBindBuffer(0x8892, 0);
            GL20.glUseProgram(0);
            if (boundTexture != 0) {
                boundTexture = 0;
                GL11.glBindTexture(0xde1, 0);
            }
        }
        reset();
    }

    int imageHandle(int image) {
        Texture texture = findTexture(image);
        return texture != null ? (int) texture.handle : 0;
    }

    public static class Framebuffer {
        VectorContext vector;
        int framebuffer;
        int renderbuffer;
        int texture;
        public int image;
    }

    public static Framebuffer createFramebuffer(VectorContext vector, int width, int height, int flags) {
        if (!GlCompat.framebuffersSupported()) {
            return null;
        }
        GlRenderer backend = (GlRenderer) vector.renderer;
        int previousFramebuffer = GL11.glGetInteger(GlCompat.FRAMEBUFFER_BINDING);
        int previousRenderbuffer = GL11.glGetInteger(GlCompat.RENDERBUFFER_BINDING);
        Framebuffer framebuffer = new Framebuffer();
        framebuffer.image = vector.createRgbaImage(width, height, flags | 0x18, null);
        framebuffer.texture = backend.imageHandle(framebuffer.image);
        framebuffer.vector = vector;
        framebuffer.framebuffer = GlCompat.genFramebuffer();
        GlCompat.bindFramebuffer(framebuffer.framebuffer);
        framebuffer.renderbuffer = GlCompat.genRenderbuffer();
        GlCompat.bindRenderbuffer(framebuffer.renderbuffer);
        GlCompat.framebufferTexture(GlCompat.COLOR_ATTACHMENT0, framebuffer.texture);
        GlCompat.renderbufferStorage(GlCompat.STENCIL_INDEX8, width, height);
        GlCompat.framebufferRenderbuffer(GlCompat.STENCIL_ATTACHMENT, framebuffer.renderbuffer);
        if (GlCompat.checkFramebufferStatus() != GlCompat.FRAMEBUFFER_COMPLETE) {
            GlCompat.renderbufferStorage(GlCompat.DEPTH24_STENCIL8, width, height);
            GlCompat.framebufferRenderbuffer(GlCompat.DEPTH_ATTACHMENT, framebuffer.renderbuffer);
            GlCompat.framebufferRenderbuffer(GlCompat.STENCIL_ATTACHMENT, framebuffer.renderbuffer);
        }
        boolean complete = GlCompat.checkFramebufferStatus() == GlCompat.FRAMEBUFFER_COMPLETE;
        GlCompat.bindFramebuffer(previousFramebuffer);
        GlCompat.bindRenderbuffer(previousRenderbuffer);
        if (!complete) {
            deleteFramebuffer(framebuffer);
            return null;
        }
        return framebuffer;
    }

    public static void deleteFramebuffer(Framebuffer framebuffer) {
        if (framebuffer == null) {
            return;
        }
        if (framebuffer.framebuffer != 0) {
            GlCompat.deleteFramebuffer(framebuffer.framebuffer);
        }
        if (framebuffer.renderbuffer != 0) {
            GlCompat.deleteRenderbuffer(framebuffer.renderbuffer);
        }
        if (framebuffer.image >= 0) {
            framebuffer.vector.deleteImage(framebuffer.image);
        }
    }

    public static int currentFramebuffer() {
        return GlCompat.framebuffersSupported() ? GL11.glGetInteger(GlCompat.FRAMEBUFFER_BINDING) : 0;
    }

    public static void bindFramebuffer(int handle) {
        if (GlCompat.framebuffersSupported()) {
            GlCompat.bindFramebuffer(handle);
        }
    }

    public static int handle(Framebuffer framebuffer) {
        return framebuffer.framebuffer;
    }
}
