package org.combustiblelemon.covenantremnants.utils.render.rupture

import com.fs.starfarer.api.Global
import org.combustiblelemon.lemonlib.render.getShaderCode
import org.lwjgl.opengl.GL11.GL_FALSE
import org.lwjgl.opengl.GL20.GL_COMPILE_STATUS
import org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER
import org.lwjgl.opengl.GL20.GL_LINK_STATUS
import org.lwjgl.opengl.GL20.GL_VERTEX_SHADER
import org.lwjgl.opengl.GL20.glAttachShader
import org.lwjgl.opengl.GL20.glCompileShader
import org.lwjgl.opengl.GL20.glCreateProgram
import org.lwjgl.opengl.GL20.glCreateShader
import org.lwjgl.opengl.GL20.glDeleteProgram
import org.lwjgl.opengl.GL20.glDeleteShader
import org.lwjgl.opengl.GL20.glGetProgramInfoLog
import org.lwjgl.opengl.GL20.glGetProgrami
import org.lwjgl.opengl.GL20.glGetShaderInfoLog
import org.lwjgl.opengl.GL20.glGetShaderi
import org.lwjgl.opengl.GL20.glGetUniformLocation
import org.lwjgl.opengl.GL20.glLinkProgram
import org.lwjgl.opengl.GL20.glShaderSource

/** A plain vertex + fragment program with cached uniform locations. Build on the GL thread. */
internal class RuptureProgram(vertexFile: String, fragmentFile: String) {
    val id: Int
    private val locations = HashMap<String, Int>()

    init {
        val vertex = compile(GL_VERTEX_SHADER, vertexFile)
        val fragment = compile(GL_FRAGMENT_SHADER, fragmentFile)
        id = glCreateProgram()
        glAttachShader(id, vertex)
        glAttachShader(id, fragment)
        glLinkProgram(id)
        if (glGetProgrami(id, GL_LINK_STATUS) == GL_FALSE) {
            Global.getLogger(RuptureProgram::class.java)
                .error("Linking $vertexFile + $fragmentFile failed: ${glGetProgramInfoLog(id, 4096)}")
        }
        glDeleteShader(vertex)
        glDeleteShader(fragment)
    }

    fun location(name: String): Int = locations.getOrPut(name) { glGetUniformLocation(id, name) }

    fun dispose() {
        glDeleteProgram(id)
    }

    private fun compile(type: Int, file: String): Int {
        val shader = glCreateShader(type)
        glShaderSource(shader, getShaderCode(file))
        glCompileShader(shader)
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            Global.getLogger(RuptureProgram::class.java)
                .error("Compiling $file failed: ${glGetShaderInfoLog(shader, 4096)}")
        }
        return shader
    }
}
