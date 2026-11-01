package com.bamnat.app.ui.theme

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import com.bamnat.core.recipe.Fill

/**
 * [MAT-01] 1.36 움직이는 채우기 (셰이더): the six shaders the app ships, in AGSL (Android 13+). A recipe names one and gives its two or three
 * colours and a speed; it never carries shader code (결정 D106). Each takes the box's size, the time in seconds and the colours.
 */
@RequiresApi(33)
internal object FillShaders {
    private const val HEAD = """
uniform float2 size;
uniform float t;
layout(color) uniform half4 c1;
layout(color) uniform half4 c2;
layout(color) uniform half4 c3;
"""

    private val SOURCES = mapOf(
        "aurora" to """
half4 main(float2 p) {
  float2 uv = p / size;
  float w1 = sin(uv.x * 3.0 + t * 0.6) * 0.15 + 0.35;
  float w2 = sin(uv.x * 5.0 - t * 0.4 + 1.3) * 0.12 + 0.62;
  float a = smoothstep(0.25, 0.0, abs(uv.y - w1));
  float b = smoothstep(0.22, 0.0, abs(uv.y - w2));
  half4 col = mix(c1, c1 * 0.55, half(uv.y));
  col = mix(col, c2, half(a * 0.85));
  col = mix(col, c3, half(b * 0.75));
  return half4(col.rgb, 1.0);
}""",
        "mesh" to """
half4 main(float2 p) {
  float2 uv = p / size;
  float2 a = float2(0.25 + 0.15 * sin(t * 0.5), 0.3 + 0.1 * cos(t * 0.4));
  float2 b = float2(0.75 + 0.1 * cos(t * 0.35), 0.35 + 0.15 * sin(t * 0.45));
  float2 c = float2(0.5 + 0.2 * sin(t * 0.3 + 2.0), 0.8 + 0.08 * cos(t * 0.5));
  float wa = 1.0 / (0.02 + dot(uv - a, uv - a));
  float wb = 1.0 / (0.02 + dot(uv - b, uv - b));
  float wc = 1.0 / (0.02 + dot(uv - c, uv - c));
  half3 col = (c1.rgb * half(wa) + c2.rgb * half(wb) + c3.rgb * half(wc)) / half(wa + wb + wc);
  return half4(col, 1.0);
}""",
        "ripple" to """
half4 main(float2 p) {
  float2 uv = (p - size * 0.5) / min(size.x, size.y);
  float d = length(uv);
  float k = sin(d * 28.0 - t * 2.4) * 0.5 + 0.5;
  half4 col = mix(c1, c2, half(k * smoothstep(0.9, 0.0, d)));
  return half4(mix(col.rgb, c3.rgb, half(smoothstep(0.35, 0.9, d) * 0.6)), 1.0);
}""",
        "plasma" to """
half4 main(float2 p) {
  float2 uv = p / min(size.x, size.y) * 3.0;
  float v = sin(uv.x + t) + sin(uv.y * 1.3 - t * 0.8) + sin((uv.x + uv.y) * 0.7 + t * 0.5) + sin(length(uv - 1.5) * 2.0 - t);
  float k = v * 0.125 + 0.5;
  half3 col = k < 0.5 ? mix(c1.rgb, c2.rgb, half(k * 2.0)) : mix(c2.rgb, c3.rgb, half(k * 2.0 - 1.0));
  return half4(col, 1.0);
}""",
        "flow" to """
half4 main(float2 p) {
  float2 uv = p / size;
  float k = fract((uv.x + uv.y) * 0.5 + t * 0.08);
  float s = k < 0.5 ? k * 2.0 : 2.0 - k * 2.0;
  half3 col = s < 0.5 ? mix(c1.rgb, c2.rgb, half(s * 2.0)) : mix(c2.rgb, c3.rgb, half(s * 2.0 - 1.0));
  return half4(col, 1.0);
}""",
        "sunset" to """
half4 main(float2 p) {
  float2 uv = p / size;
  half3 sky = mix(c1.rgb, c2.rgb, half(smoothstep(0.0, 0.85, uv.y)));
  float2 sun = float2(0.5, 0.72 + 0.03 * sin(t * 0.3));
  float d = length((uv - sun) * float2(size.x / size.y, 1.0));
  sky = mix(sky, c3.rgb, half(smoothstep(0.16, 0.12, d)));
  sky += half3(c3.rgb) * half(smoothstep(0.5, 0.0, d) * 0.25);
  return half4(sky, 1.0);
}""",
    )

    fun make(fill: Fill.Shader): RuntimeShader? = SOURCES[fill.kind]?.let { runCatching { RuntimeShader(HEAD + it) }.getOrNull() }

    fun update(shader: Any, fill: Fill.Shader, w: Float, h: Float, seconds: Float) {
        if (Build.VERSION.SDK_INT < 33 || shader !is RuntimeShader) return
        shader.setFloatUniform("size", w.coerceAtLeast(1f), h.coerceAtLeast(1f))
        shader.setFloatUniform("t", seconds)
        val cs = fill.colors
        shader.setColorUniform("c1", cs[0].toInt())
        shader.setColorUniform("c2", cs.getOrElse(1) { cs[0] }.toInt())
        shader.setColorUniform("c3", cs.getOrElse(2) { cs.getOrElse(1) { cs[0] } }.toInt())
    }
}
