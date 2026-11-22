package pt.pine3.view

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Destino de um caminho SVG (sem dependências do Android, para poder ser testado na JVM). */
interface PathSink {
    fun moveTo(x: Float, y: Float)
    fun lineTo(x: Float, y: Float)
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float)
    fun close()
}

/** Leitor de `d="..."` de SVG: M L H V C S Q T A Z (maiúsculas e minúsculas), incluindo arcos e flags compactas. */
object SvgPath {
    fun parse(d: String, out: PathSink) {
        val r = Reader(d)
        var cx = 0.0; var cy = 0.0          // posição atual
        var sx = 0.0; var sy = 0.0          // início do subcaminho
        var lcx = 0.0; var lcy = 0.0        // último ponto de controlo (para S/T)
        var prev = ' '
        var cmd = ' '
        while (true) {
            r.skipSep()
            if (r.end()) break
            val c = r.peek()
            if (c.isLetter()) { cmd = c; r.next() } else if (cmd == ' ') throw IllegalArgumentException("caminho inválido")
            val rel = cmd.isLowerCase()
            when (cmd.uppercaseChar()) {
                'Z' -> { out.close(); cx = sx; cy = sy; prev = 'Z'; continue }
                'M' -> {
                    val x = r.num() + if (rel) cx else 0.0; val y = r.num() + if (rel) cy else 0.0
                    out.moveTo(x.toFloat(), y.toFloat()); cx = x; cy = y; sx = x; sy = y
                    cmd = if (rel) 'l' else 'L'; prev = 'M'
                }
                'L' -> { val x = r.num() + if (rel) cx else 0.0; val y = r.num() + if (rel) cy else 0.0; out.lineTo(x.toFloat(), y.toFloat()); cx = x; cy = y; prev = 'L' }
                'H' -> { val x = r.num() + if (rel) cx else 0.0; out.lineTo(x.toFloat(), cy.toFloat()); cx = x; prev = 'H' }
                'V' -> { val y = r.num() + if (rel) cy else 0.0; out.lineTo(cx.toFloat(), y.toFloat()); cy = y; prev = 'V' }
                'C' -> {
                    val ox = if (rel) cx else 0.0; val oy = if (rel) cy else 0.0
                    val x1 = r.num() + ox; val y1 = r.num() + oy; val x2 = r.num() + ox; val y2 = r.num() + oy
                    val x = r.num() + ox; val y = r.num() + oy
                    out.cubicTo(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), x.toFloat(), y.toFloat())
                    lcx = x2; lcy = y2; cx = x; cy = y; prev = 'C'
                }
                'S' -> {
                    val ox = if (rel) cx else 0.0; val oy = if (rel) cy else 0.0
                    val x2 = r.num() + ox; val y2 = r.num() + oy; val x = r.num() + ox; val y = r.num() + oy
                    val x1 = if (prev == 'C' || prev == 'S') 2 * cx - lcx else cx
                    val y1 = if (prev == 'C' || prev == 'S') 2 * cy - lcy else cy
                    out.cubicTo(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), x.toFloat(), y.toFloat())
                    lcx = x2; lcy = y2; cx = x; cy = y; prev = 'S'
                }
                'Q' -> {
                    val ox = if (rel) cx else 0.0; val oy = if (rel) cy else 0.0
                    val qx = r.num() + ox; val qy = r.num() + oy; val x = r.num() + ox; val y = r.num() + oy
                    quad(out, cx, cy, qx, qy, x, y)
                    lcx = qx; lcy = qy; cx = x; cy = y; prev = 'Q'
                }
                'T' -> {
                    val ox = if (rel) cx else 0.0; val oy = if (rel) cy else 0.0
                    val x = r.num() + ox; val y = r.num() + oy
                    val qx = if (prev == 'Q' || prev == 'T') 2 * cx - lcx else cx
                    val qy = if (prev == 'Q' || prev == 'T') 2 * cy - lcy else cy
                    quad(out, cx, cy, qx, qy, x, y)
                    lcx = qx; lcy = qy; cx = x; cy = y; prev = 'T'
                }
                'A' -> {
                    val rx = r.num(); val ry = r.num(); val rot = r.num()
                    val fa = r.flag(); val fs = r.flag()
                    val x = r.num() + if (rel) cx else 0.0; val y = r.num() + if (rel) cy else 0.0
                    arc(out, cx, cy, rx, ry, rot, fa, fs, x, y)
                    cx = x; cy = y; prev = 'A'
                }
                else -> throw IllegalArgumentException("comando $cmd")
            }
        }
    }

    private fun quad(out: PathSink, x0: Double, y0: Double, qx: Double, qy: Double, x: Double, y: Double) {
        out.cubicTo(
            (x0 + 2.0 / 3 * (qx - x0)).toFloat(), (y0 + 2.0 / 3 * (qy - y0)).toFloat(),
            (x + 2.0 / 3 * (qx - x)).toFloat(), (y + 2.0 / 3 * (qy - y)).toFloat(), x.toFloat(), y.toFloat(),
        )
    }

    private fun angle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
        val dot = ux * vx + uy * vy
        val len = sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy)
        var a = acos((dot / len).coerceIn(-1.0, 1.0))
        if (ux * vy - uy * vx < 0) a = -a
        return a
    }

    /** Arco SVG (parametrização pelos pontos) convertido em curvas de Bézier de no máximo 90°. */
    private fun arc(out: PathSink, x0: Double, y0: Double, rx0: Double, ry0: Double, rot: Double, fa: Boolean, fs: Boolean, x: Double, y: Double) {
        var rx = abs(rx0); var ry = abs(ry0)
        if (rx == 0.0 || ry == 0.0 || (x0 == x && y0 == y)) { out.lineTo(x.toFloat(), y.toFloat()); return }
        val phi = Math.toRadians(rot % 360)
        val cosP = cos(phi); val sinP = sin(phi)
        val dx2 = (x0 - x) / 2; val dy2 = (y0 - y) / 2
        val x1p = cosP * dx2 + sinP * dy2
        val y1p = -sinP * dx2 + cosP * dy2
        val lambda = x1p * x1p / (rx * rx) + y1p * y1p / (ry * ry)
        if (lambda > 1) { val s = sqrt(lambda); rx *= s; ry *= s }
        val num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
        val den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
        val coef = (if (fa == fs) -1 else 1) * sqrt(max(0.0, num / den))
        val cxp = coef * rx * y1p / ry
        val cyp = -coef * ry * x1p / rx
        val cx = cosP * cxp - sinP * cyp + (x0 + x) / 2
        val cy = sinP * cxp + cosP * cyp + (y0 + y) / 2
        val theta1 = angle(1.0, 0.0, (x1p - cxp) / rx, (y1p - cyp) / ry)
        var dTheta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
        if (!fs && dTheta > 0) dTheta -= 2 * Math.PI else if (fs && dTheta < 0) dTheta += 2 * Math.PI
        val segs = max(1, ceil(abs(dTheta) / (Math.PI / 2) - 1e-9).toInt())
        val delta = dTheta / segs
        val t = 4.0 / 3 * tan(delta / 4)
        var th = theta1
        for (i in 0 until segs) {
            val c1 = cos(th); val s1 = sin(th); val c2 = cos(th + delta); val s2 = sin(th + delta)
            // pontos do arco na elipse unitária, depois escala, rotação e translação
            fun px(ux: Double, uy: Double) = cosP * rx * ux - sinP * ry * uy + cx
            fun py(ux: Double, uy: Double) = sinP * rx * ux + cosP * ry * uy + cy
            out.cubicTo(
                px(c1 - t * s1, s1 + t * c1).toFloat(), py(c1 - t * s1, s1 + t * c1).toFloat(),
                px(c2 + t * s2, s2 - t * c2).toFloat(), py(c2 + t * s2, s2 - t * c2).toFloat(),
                px(c2, s2).toFloat(), py(c2, s2).toFloat(),
            )
            th += delta
        }
    }

    private class Reader(val s: String) {
        var i = 0
        fun end() = i >= s.length
        fun peek() = s[i]
        fun next() = s[i++]
        fun skipSep() { while (i < s.length && (s[i] == ' ' || s[i] == ',' || s[i] == '\n' || s[i] == '\t' || s[i] == '\r')) i++ }

        /** Flag de arco: um único '0' ou '1' (pode vir colada ao número seguinte). */
        fun flag(): Boolean {
            skipSep()
            val c = s[i++]
            if (c != '0' && c != '1') throw IllegalArgumentException("flag inválida")
            return c == '1'
        }

        fun num(): Double {
            skipSep()
            val start = i
            if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
            var dot = false
            while (i < s.length) {
                val c = s[i]
                if (c in '0'..'9') i++
                else if (c == '.' && !dot) { dot = true; i++ }
                else break
            }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                while (i < s.length && s[i] in '0'..'9') i++
            }
            if (start == i) throw IllegalArgumentException("número esperado em $start")
            return s.substring(start, i).toDouble()
        }
    }
}
