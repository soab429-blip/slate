package yt.ren.slate.ui.backend.gl;

import org.lwjgl.opengl.ARBFramebufferObject;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

final class GlCompat {
    static final int FRAMEBUFFER = 0x8d40;
    static final int RENDERBUFFER = 0x8d41;
    static final int FRAMEBUFFER_BINDING = 0x8ca6;
    static final int RENDERBUFFER_BINDING = 0x8ca7;
    static final int FRAMEBUFFER_COMPLETE = 0x8cd5;
    static final int COLOR_ATTACHMENT0 = 0x8ce0;
    static final int DEPTH_ATTACHMENT = 0x8d00;
    static final int STENCIL_ATTACHMENT = 0x8d20;
    static final int STENCIL_INDEX8 = 0x8d48;
    static final int DEPTH24_STENCIL8 = 0x88f0;

    private static final int CORE = 1;
    private static final int ARB = 2;
    private static final int EXT = 3;

    private static boolean initialized;
    private static int framebuffers;
    private static boolean textureRg;

    static void init() {
        if (initialized) {
            return;
        }
        ContextCapabilities caps = GLContext.getCapabilities();
        if (caps.OpenGL30) {
            framebuffers = CORE;
        } else if (caps.GL_ARB_framebuffer_object) {
            framebuffers = ARB;
        } else if (caps.GL_EXT_framebuffer_object) {
            framebuffers = EXT;
        }
        textureRg = caps.OpenGL30 || caps.GL_ARB_texture_rg;
        initialized = true;
    }

    static boolean framebuffersSupported() {
        init();
        return framebuffers != 0;
    }

    static int alphaFormat() {
        init();
        return textureRg ? 0x1903 : 0x1909;
    }

    static void generateMipmap(int target) {
        init();
        switch (framebuffers) {
            case CORE:
                GL30.glGenerateMipmap(target);
                break;
            case ARB:
                ARBFramebufferObject.glGenerateMipmap(target);
                break;
            case EXT:
                EXTFramebufferObject.glGenerateMipmapEXT(target);
                break;
            default:
                GL11.glTexParameteri(target, GL14.GL_GENERATE_MIPMAP, GL11.GL_TRUE);
                break;
        }
    }

    static int genFramebuffer() {
        switch (framebuffers) {
            case CORE:
                return GL30.glGenFramebuffers();
            case ARB:
                return ARBFramebufferObject.glGenFramebuffers();
            default:
                return EXTFramebufferObject.glGenFramebuffersEXT();
        }
    }

    static void bindFramebuffer(int framebuffer) {
        switch (framebuffers) {
            case CORE:
                GL30.glBindFramebuffer(FRAMEBUFFER, framebuffer);
                break;
            case ARB:
                ARBFramebufferObject.glBindFramebuffer(FRAMEBUFFER, framebuffer);
                break;
            default:
                EXTFramebufferObject.glBindFramebufferEXT(FRAMEBUFFER, framebuffer);
                break;
        }
    }

    static void deleteFramebuffer(int framebuffer) {
        switch (framebuffers) {
            case CORE:
                GL30.glDeleteFramebuffers(framebuffer);
                break;
            case ARB:
                ARBFramebufferObject.glDeleteFramebuffers(framebuffer);
                break;
            default:
                EXTFramebufferObject.glDeleteFramebuffersEXT(framebuffer);
                break;
        }
    }

    static int genRenderbuffer() {
        switch (framebuffers) {
            case CORE:
                return GL30.glGenRenderbuffers();
            case ARB:
                return ARBFramebufferObject.glGenRenderbuffers();
            default:
                return EXTFramebufferObject.glGenRenderbuffersEXT();
        }
    }

    static void bindRenderbuffer(int renderbuffer) {
        switch (framebuffers) {
            case CORE:
                GL30.glBindRenderbuffer(RENDERBUFFER, renderbuffer);
                break;
            case ARB:
                ARBFramebufferObject.glBindRenderbuffer(RENDERBUFFER, renderbuffer);
                break;
            default:
                EXTFramebufferObject.glBindRenderbufferEXT(RENDERBUFFER, renderbuffer);
                break;
        }
    }

    static void deleteRenderbuffer(int renderbuffer) {
        switch (framebuffers) {
            case CORE:
                GL30.glDeleteRenderbuffers(renderbuffer);
                break;
            case ARB:
                ARBFramebufferObject.glDeleteRenderbuffers(renderbuffer);
                break;
            default:
                EXTFramebufferObject.glDeleteRenderbuffersEXT(renderbuffer);
                break;
        }
    }

    static void renderbufferStorage(int format, int width, int height) {
        switch (framebuffers) {
            case CORE:
                GL30.glRenderbufferStorage(RENDERBUFFER, format, width, height);
                break;
            case ARB:
                ARBFramebufferObject.glRenderbufferStorage(RENDERBUFFER, format, width, height);
                break;
            default:
                EXTFramebufferObject.glRenderbufferStorageEXT(RENDERBUFFER, format, width, height);
                break;
        }
    }

    static void framebufferTexture(int attachment, int texture) {
        switch (framebuffers) {
            case CORE:
                GL30.glFramebufferTexture2D(FRAMEBUFFER, attachment, GL11.GL_TEXTURE_2D, texture, 0);
                break;
            case ARB:
                ARBFramebufferObject.glFramebufferTexture2D(FRAMEBUFFER, attachment, GL11.GL_TEXTURE_2D, texture, 0);
                break;
            default:
                EXTFramebufferObject.glFramebufferTexture2DEXT(FRAMEBUFFER, attachment, GL11.GL_TEXTURE_2D, texture, 0);
                break;
        }
    }

    static void framebufferRenderbuffer(int attachment, int renderbuffer) {
        switch (framebuffers) {
            case CORE:
                GL30.glFramebufferRenderbuffer(FRAMEBUFFER, attachment, RENDERBUFFER, renderbuffer);
                break;
            case ARB:
                ARBFramebufferObject.glFramebufferRenderbuffer(FRAMEBUFFER, attachment, RENDERBUFFER, renderbuffer);
                break;
            default:
                EXTFramebufferObject.glFramebufferRenderbufferEXT(FRAMEBUFFER, attachment, RENDERBUFFER, renderbuffer);
                break;
        }
    }

    static int checkFramebufferStatus() {
        switch (framebuffers) {
            case CORE:
                return GL30.glCheckFramebufferStatus(FRAMEBUFFER);
            case ARB:
                return ARBFramebufferObject.glCheckFramebufferStatus(FRAMEBUFFER);
            default:
                return EXTFramebufferObject.glCheckFramebufferStatusEXT(FRAMEBUFFER);
        }
    }
}
