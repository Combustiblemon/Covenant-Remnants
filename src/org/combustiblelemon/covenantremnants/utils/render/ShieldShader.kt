package org.combustiblelemon.covenantremnants.utils.render

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ViewportAPI
import org.combustiblelemon.covenantremnants.utils.Utils
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.util.vector.Vector2f

enum class SmoothingMethod {
    SIMPLE,   // Uses edge smoothing by repositioning points
    SPLINE,   // Uses Laplacian smoothing for natural shape smoothing
    NONE      // Uses minimal smoothing for very subtle adjustments
}

// TODO: Fix ripple effects to be consistent across zoom levels. Fix ripple radius and width to be consistent across zoom levels. Ripple is much faster zoomed out than zoomed in.

class ShieldShader(
    private val ship: ShipAPI,
    private var shipDimensions: Array<Vector2f>,
    private var popEffectRadius: Float = 500f,  // Default pop effect radius
    private val boundsCount: Int,
    private val smoothingMethod: SmoothingMethod = SmoothingMethod.NONE
) {
    private var polygonPoints: List<Float> = listOf()
    private var colorRGBA: FloatArray = floatArrayOf(0.2f, 0.4f, 1.0f, 1f)
    private var borderColorRGBA: FloatArray = floatArrayOf(1f, 1f, 1f, 1f)
    private var borderWidth: Float = 10f
    private var glowIntensity: Float = 0f
    private var glowWidth: Float = 20f
    private var vbo: Int = 0
    private var vao: Int = 0
    private var shaderProgram: Int = 0

    // No UBO needed - using direct uniforms instead
    private var scaleX: Float = 1.0f
    private var scaleY: Float = 1.0f
    private var screenWidth: Float = 1920f
    private var screenHeight: Float = 1080f
    private var zoomLevel: Float = 1f
    private var splineSubdivisions: Int = 8
    private var debugMode: Boolean = false
    private var enableSplineSmoothing: Boolean = true
    private var enableBoundingBoxOptimization: Boolean = true // Re-enabled, scissor test disabled

    // Pre-calculated rotation matrix for vertex shader optimization
    private var shipRotationMatrix: FloatArray = floatArrayOf(1f, 0f, 0f, 1f) // Identity matrix

    // Viewport scale for world-to-screen conversion
    private var viewportScaleX: Float = 1.0f
    private var viewportScaleY: Float = 1.0f

    // Pre-calculated values for performance
    private var shieldBoundsMinX: Float = 0f
    private var shieldBoundsMaxX: Float = 0f
    private var effectiveMinX: Float = 0f
    private var effectiveMaxX: Float = 0f
    private var effectiveWidth: Float = 0f

    // Sweep position calculated on CPU
    private var sweepPosition: Float = 0f

    // Bounding box for optimization
    private var boundingBoxMinX: Float = 0f
    private var boundingBoxMaxX: Float = 0f
    private var boundingBoxMinY: Float = 0f
    private var boundingBoxMaxY: Float = 0f
    private var boundingBoxWidth: Float = 0f
    private var boundingBoxHeight: Float = 0f

    // Add animation properties
    private var time = 0f
    private var shieldHPPercent = 1f

    // Honeycomb parameters - hardcoded
    private var honeycombEnabled = true
    private var honeycombSize = 10.0f
    private var edgeWidth = 3.0f

    private data class Ripple(
        var center: Pair<Float, Float>,
        var radius: Float,
        var width: Float,
        var color: FloatArray,
        var time: Float,
        var intensity: Float,
        var maxDistance: Float
    )

    private val activeRipples = mutableListOf<Ripple>()

    private data class PopWave(
        var time: Float = 0f,
        var maxTime: Float = 0.8f,
        var color: FloatArray = floatArrayOf(0f, 0f, 0f, 0f),
        var darkerColor: FloatArray = floatArrayOf(0f, 0f, 0f, 0f),
        var thickness: Float = 20f,
        var startScale: Float = 0.1f,
        var endScale: Float = 3.0f,
        var intensity: Float = 1.0f,
        var radialLines: List<RadialLine> = listOf()
    )

    private var activePopWave: PopWave? = null

    private val noiseSprite = Global.getSettings().getSprite("covenant_remnants", "shield_noise")

    private data class RadialLine(
        val angle: Float,       // Angle in radians
        val length: Float,      // Length of the line (0-1, will be multiplied by radius)
        val startOffset: Float, // Offset from center (0-1, will be multiplied by radius)
        val thickness: Float,   // Thickness of the line
        val alpha: Float,       // Base alpha/opacity
        val speedMultiplier: Float  // How fast this line expands compared to others
    )

    // Shader source code
    private val vertexShaderSource = """
        #version 330 core
        layout (location = 0) in vec2 aPos;        // NDC coordinates for full-screen quad
        layout (location = 1) in vec2 worldPoint;  // World coordinates of polygon point

        out vec2 TexCoord;
        out vec2 screenPos;           // Pre-calculated screen position
        out vec2 shipRelPos;          // Pre-calculated ship-relative position
        out float distanceFromCenter; // Pre-calculated distance from ship center
        out float shipRelX;           // Pre-calculated ship-relative X coordinate for sweep

        uniform vec2 resolution;
        uniform vec2 center;
        uniform vec2 scale;
        uniform vec4 shipRotationMatrix; // Pre-calculated rotation matrix as vec4 (m00, m01, m10, m11)
        uniform vec2 viewportScale;      // For world-to-screen conversion

        void main() {
            // Pass through the NDC position for full-screen quad
            gl_Position = vec4(aPos, 0.0, 1.0);

            // Convert NDC to texture coordinates (0 to 1)
            TexCoord = aPos * 0.5 + 0.5;

            // Transform world point through all stages (moved from CPU)
            // Note: worldPoint is currently dummy (0,0) for quad vertices
            // The actual polygon points are passed as uniforms to the fragment shader
            vec2 scaledPoint = worldPoint * scale;
            vec2 translatedPoint = scaledPoint + center;
            vec2 rotatedPoint = vec2(
                shipRotationMatrix.x * (translatedPoint.x - center.x) + shipRotationMatrix.y * (translatedPoint.y - center.y) + center.x,
                shipRotationMatrix.z * (translatedPoint.x - center.x) + shipRotationMatrix.w * (translatedPoint.y - center.y) + center.y
            );
            vec2 screenPoint = rotatedPoint * viewportScale;

            // Pre-calculate values for fragment shader
            screenPos = screenPoint;

            // Pre-calculate ship-relative position
            vec2 screenPointRel = screenPoint - center;
            shipRelPos = vec2(
                shipRotationMatrix.x * screenPointRel.x + shipRotationMatrix.y * screenPointRel.y,
                shipRotationMatrix.z * screenPointRel.x + shipRotationMatrix.w * screenPointRel.y
            );

            // Pre-calculate ship-relative X coordinate for sweep animation
            shipRelX = shipRelPos.x;

            // Pre-calculate distance from center (useful for many effects)
            distanceFromCenter = length(screenPointRel);
        }
    """.trimIndent()

    private val fragmentShaderSource = """
        #version 330 core
        out vec4 FragColor;

        in vec2 TexCoord;
        in vec2 screenPos;           // Pre-calculated screen position from vertex shader
        in vec2 shipRelPos;          // Pre-calculated ship-relative position from vertex shader
        in float distanceFromCenter; // Pre-calculated distance from ship center from vertex shader
        in float shipRelX;           // Pre-calculated ship-relative X coordinate from vertex shader

        // Uniforms
        uniform vec4 shieldColor;
        uniform vec4 borderColor;
        uniform float borderWidth;
        uniform float glowIntensity;
        uniform float glowWidth;
        uniform float chargeProgress;
        uniform float shipFacing;
        uniform vec2 center;
        uniform float time;
        uniform bool honeycombEnabled;
        uniform float honeycombSize;
        uniform float edgeWidth;
        uniform vec2 scale;
        uniform float rotation;
        uniform float shieldSweepProgress;  // 0.0 to 1.0, controls sweep animation
        uniform float sweepEdgeWidth;       // Width of the sweep edge in pixels
        uniform float shieldHPPercent;      // 0.0 to 1.0, shield health percentage



        // Sweep position calculated on CPU
        uniform float sweepPosition;        // Current sweep position in ship-relative coordinates

        // Ripple uniforms
        uniform int numRipples;
        uniform vec4 rippleColors[10];
        uniform vec2 rippleCenters[10];
        uniform float rippleRadii[10];
        uniform float rippleIntensities[10];
        uniform float rippleWidths[10];

        // Pop wave uniforms
        uniform bool popWaveActive;
        uniform float popWaveTime;
        uniform float popWaveMaxTime;
        uniform vec4 popWaveColor;
        uniform vec4 popWaveDarkerColor;
        uniform float popWaveThickness;
        uniform float popWaveStartScale;
        uniform float popWaveEndScale;
        uniform float popWaveIntensity;
        uniform float popEffectRadius;

        // Bounding box uniforms for debug visualization
        uniform vec2 boundingBoxMin;
        uniform vec2 boundingBoxMax;

        // Polygon points as individual uniform arrays - no UBO needed
        uniform vec2 points[POINT_COUNT_PLACEHOLDER]; // Screen coordinates

        uniform bool debugPolygon;    // Debug flag for polygon rendering

        uniform vec2 resolution;   // Screen resolution
        uniform float zoomLevel;   // Viewport zoom level (viewMult)
        uniform bool debugMode;    // Debug mode to show polygon outline

        // Additional uniforms needed for coordinate transformations
        uniform vec4 shipRotationMatrix; // Pre-calculated rotation matrix as vec4 (m00, m01, m10, m11)
        uniform vec2 viewportScale;      // For world-to-screen conversion



        // Helper function to calculate distance from point to line segment
        float distanceToLine(vec2 point, vec2 lineStart, vec2 lineEnd) {
            vec2 line = lineEnd - lineStart;
            vec2 pointToStart = point - lineStart;
            float t = clamp(dot(pointToStart, line) / dot(line, line), 0.0, 1.0);
            vec2 closest = lineStart + t * line;
            return distance(point, closest);
        }

                                          // Helper function to render honeycomb pattern covering entire shield
         float calculateHoneycomb(vec2 pixelPos) {
             // Convert pixel position to ship-relative coordinates
             vec2 shipRelPos = pixelPos - center;

             // Use ship facing to calculate the correct x-axis direction for grid rotation
             float facingRad = radians(shipFacing);
             float cosFacing = cos(facingRad);
             float sinFacing = sin(facingRad);

             // Rotate the position to align with ship's x-axis (facing direction)
             vec2 localPos = vec2(
                 shipRelPos.x * cosFacing + shipRelPos.y * sinFacing,
                 -shipRelPos.x * sinFacing + shipRelPos.y * cosFacing
             );

                             // Configurable hex parameters
               float hexRadius = honeycombSize / viewportScale.x;  // Use uniform honeycombSize
               float edgeThickness = edgeWidth / viewportScale.x;  // Use uniform edgeWidth

             // Proper honeycomb spacing - hexagons with touching sides
             float hexSpacingX = hexRadius * 1.4;      // 1.5 * radius for touching hexagons
             float hexSpacingY = hexRadius * 1.2;      // Even closer vertical spacing for overlapping rows

             // Calculate which hexagon this pixel belongs to
             // First, determine which row we're in
             float hexY = localPos.y / hexSpacingY;
             int gridY = int(round(hexY));

             // Calculate row offset first
             float rowOffset = 0.0;
             if (mod(float(gridY), 2.0) != 0.0) {
                 rowOffset = hexSpacingX * 0.5;
             }

             // Now calculate x position accounting for the row offset
             float hexX = (localPos.x - rowOffset) / hexSpacingX;
             int gridX = int(round(hexX));

             // Calculate hex center position
             vec2 hexCenter = vec2(
                 float(gridX) * hexSpacingX + rowOffset,
                 float(gridY) * hexSpacingY
             );

             // Calculate distance from pixel to hex center
             float distFromCenter = distance(localPos, hexCenter);

                                                                 // Simple hexagon edge calculation with proper scaling and rotation
             float distToHexEdge = 0.0;
             if (distFromCenter > 0.0) {
                                  // Calculate angle from hex center to pixel
                 vec2 dir = (localPos - hexCenter) / distFromCenter;
                 float angle = atan(dir.y, dir.x);
                 if (angle < 0.0) angle += 2.0 * 3.14159265359;

                 // Rotate by 90 degrees (π/2) so sides touch instead of points
                 angle += 3.14159265359 / 2.0;

                 // Normalize angle to 0-60 degree range (hexagon has 6-fold symmetry)
                 float normalizedAngle = mod(angle, 3.14159265359 / 3.0);

                 // Simple hexagon edge distance with proper scaling
                 float hexEdgeDist = (hexRadius * 0.7) / cos(normalizedAngle - 3.14159265359 / 6.0);

                 distToHexEdge = abs(distFromCenter - hexEdgeDist);
             }

             // Calculate hex effect
             if (distToHexEdge < edgeThickness) {
                 // Simple animation
                 float animationFreq = 0.01 * viewportScale.x;
                 float pixelAlpha = 0.5 + 0.5 * sin(time + localPos.x * animationFreq + localPos.y * animationFreq * 0.6);
                 pixelAlpha = max(0.5, pixelAlpha);

                 // Calculate edge factor
                 float edgeFactor = 1.0 - (distToHexEdge / edgeThickness);
                 return pixelAlpha * edgeFactor;
             }

             return 0.0;
         }

                                // Point-in-polygon test using ray casting algorithm
        bool isPointInPolygon(vec2 point) {
            // Use the fixed array size from POINT_COUNT_PLACEHOLDER
            const int numPoints = POINT_COUNT_PLACEHOLDER;
            if (numPoints < 3) return false;

            bool inside = false;
            int j = numPoints - 1;

            for (int i = 0; i < numPoints; i++) {
                // Points are already in screen coordinates
                vec2 screenPointI = points[i];
                vec2 screenPointJ = points[j];

                // Check if point is on the same side of the edge as the ray
                if (((screenPointI.y > point.y) != (screenPointJ.y > point.y)) &&
                    (point.x < (screenPointJ.x - screenPointI.x) * (point.y - screenPointI.y) / (screenPointJ.y - screenPointI.y) + screenPointI.x)) {
                    inside = !inside;
                }
                j = i;
            }

            return inside;
        }

                                // Shield bounds are now pre-calculated on CPU for performance



                vec4 mainShield(vec2 pixelPos) {
            // Calculate shield color with charge progress and HP-based brightness
            vec4 finalShieldColor = shieldColor;

            // Make shield brighter as HP decreases (inverse relationship)
            // When shieldHPPercent is 1.0 (full), brightness is 1.0
            // When shieldHPPercent is 0.0 (empty), brightness is 2.0 (twice as bright)
            float hpBrightness = 2.0 - shieldHPPercent;
            finalShieldColor.rgb *= hpBrightness;

            // Make shield less transparent (more opaque) as HP decreases
            // When shieldHPPercent is 1.0 (full), alpha is unchanged (1.0 multiplier)
            // When shieldHPPercent is 0.0 (empty), alpha is increased (1.0 multiplier, fully opaque)
            float hpAlphaMultiplier = 1.0 - (shieldHPPercent * 0.0); // Range from 1.0 to 1.0 (no change)
            // Alternative: make it more opaque only when damaged
            float damageAlphaBoost = (1.0 - shieldHPPercent) * 0.6; // 0.0 to 0.3 boost when damaged
            finalShieldColor.a *= (1.0 + damageAlphaBoost);

            // Calculate ship-relative position in screen coordinates
            // Get ship-relative position (relative to ship center in screen coordinates)
            vec2 shipRelPos = pixelPos - center;
            // Apply rotation matrix to get ship-relative X coordinate (same as CPU calculation)
            // shipRotationMatrix[0] = cos(facing), shipRotationMatrix[1] = -sin(facing)
            // But we need +sin(facing) to match CPU calculation, so we negate shipRotationMatrix[1]
            float pixelShipRelX = shipRelPos.x * shipRotationMatrix[0] - shipRelPos.y * shipRotationMatrix[1];

            // Calculate sweep edge color based on distance from sweep position
            // Scale edge width with screen resolution for consistent visual size
            float scaledEdgeWidth = sweepEdgeWidth * (resolution.x / 1920.0);
            float halfEdgeWidth = scaledEdgeWidth * 0.5;
            float distanceFromSweep = abs(pixelShipRelX - sweepPosition);

            vec4 sweepEdgeColor = vec4(0.0, 0.0, 0.0, 0.0); // Default transparent
            if (distanceFromSweep < halfEdgeWidth) {
                float edgeFactor = 1.0 - (distanceFromSweep / halfEdgeWidth);
                sweepEdgeColor = vec4(borderColor.rgb, borderColor.a * edgeFactor);
            }

            // If pixel is beyond the sweep position in ship-relative coordinates, make it transparent
            if (pixelShipRelX < sweepPosition) {
                // If within sweep edge, show edge color, otherwise transparent
                return sweepEdgeColor;
            }

            // Apply border effect
            float minDistToEdge = 1000.0;
            const int numPoints = POINT_COUNT_PLACEHOLDER;
            int j = numPoints - 1;

            for(int i = 0; i < numPoints; i++) {
                float dist = distanceToLine(pixelPos, points[i], points[j]);
                minDistToEdge = min(minDistToEdge, dist);
                j = i;
            }

            // Start with base shield color
            vec4 resultColor = finalShieldColor;

            // Apply regular border
            if (minDistToEdge < borderWidth) {
                float borderFactor = 1.0 - (minDistToEdge / borderWidth);
                resultColor = mix(resultColor, borderColor, borderFactor);
            }

            // Apply sweep edge effect (blend with existing color)
            if (sweepEdgeColor.a > 0.0) {
                // Blend sweep edge with the current shield color
                resultColor = mix(resultColor, sweepEdgeColor, sweepEdgeColor.a);
            }

                        // Apply ripple effects
            for (int i = 0; i < numRipples; i++) {
                float distanceToRipple = distance(pixelPos, rippleCenters[i]);
                // Use ripple radius and width directly (already converted to screen coordinates)
                float rippleRadius = rippleRadii[i];
                float rippleWidth = rippleWidths[i];

                // Check if pixel is within the ripple ring
                if (distanceToRipple >= rippleRadius - rippleWidth && distanceToRipple <= rippleRadius) {
                    // Scale the fade calculation to maintain consistent visual appearance across zoom levels
                    // When zoomed out (viewportScale.x > 1), we want sharper fades
                    // When zoomed in (viewportScale.x < 1), we want more gradual fades
                    float fadeWidth = rippleWidth / viewportScale.x;
                    float rippleFactor = 1.0 - abs(distanceToRipple - rippleRadius) / fadeWidth;
                    rippleFactor = clamp(rippleFactor, 0.0, 1.0);
                    rippleFactor *= rippleIntensities[i];

                    // Blend ripple color with current color
                    resultColor = mix(resultColor, rippleColors[i], rippleFactor * rippleColors[i].a);
                }
            }

                                                 // Apply honeycomb effect
             if (honeycombEnabled) {
                 float honeycombEffect = calculateHoneycomb(pixelPos);
                 if (honeycombEffect > 0.0) {
                     vec4 honeycombColor = vec4(1.0, 1.0, 1.0, honeycombEffect);
                     resultColor = mix(resultColor, honeycombColor, honeycombEffect);
                 }
             }

            return resultColor;
        }

        void main() {
            // Calculate pixel position from texture coordinates (this is the actual screen position)
            vec2 pixelPos = TexCoord * resolution;

            // Debug mode: show polygon outline, points, and bounding box
            if(debugMode) {
                const int numPoints = POINT_COUNT_PLACEHOLDER;

                // Show bounding box outline (red rectangle)
                float boundingBoxLeft = boundingBoxMin.x;
                float boundingBoxRight = boundingBoxMax.x;
                float boundingBoxTop = boundingBoxMax.y;
                float boundingBoxBottom = boundingBoxMin.y;

                // Check if pixel is near bounding box edges (increased thickness for visibility)
                float distToLeft = abs(pixelPos.x - boundingBoxLeft);
                float distToRight = abs(pixelPos.x - boundingBoxRight);
                float distToTop = abs(pixelPos.y - boundingBoxTop);
                float distToBottom = abs(pixelPos.y - boundingBoxBottom);

                bool nearLeftEdge = distToLeft < 5.0 && pixelPos.y >= boundingBoxBottom && pixelPos.y <= boundingBoxTop;
                bool nearRightEdge = distToRight < 5.0 && pixelPos.y >= boundingBoxBottom && pixelPos.y <= boundingBoxTop;
                bool nearTopEdge = distToTop < 5.0 && pixelPos.x >= boundingBoxLeft && pixelPos.x <= boundingBoxRight;
                bool nearBottomEdge = distToBottom < 5.0 && pixelPos.x >= boundingBoxLeft && pixelPos.x <= boundingBoxRight;

                if(nearLeftEdge || nearRightEdge || nearTopEdge || nearBottomEdge) {
                    FragColor = vec4(1.0, 0.0, 0.0, 1.0); // Red bounding box outline
                    return;
                }

                // Show all polygon points as small circles (only valid points)
                // Points are already in screen coordinates
                for(int i = 0; i < numPoints; i++) {
                    float dist = distance(pixelPos, points[i]);
                    if(dist < 3.0) {
                        FragColor = vec4(0.0, 1.0, 0.0, 1.0); // Green dots for points
                        return;
                    }
                }

                // Check if pixel is near any polygon edge (only valid edges)
                // Points are already in screen coordinates
                float minDist = 1000.0;
                int j = numPoints - 1;

                for(int i = 0; i < numPoints; i++) {
                    float dist = distanceToLine(pixelPos, points[i], points[j]);
                    minDist = min(minDist, dist);
                    j = i;
                }

                if(minDist < 2.0) {
                    FragColor = vec4(1.0, 0.0, 1.0, 1.0); // Magenta outline
                    return;
                }
            }

            // Check if pixel is inside the shield polygon
            bool insidePolygon = isPointInPolygon(pixelPos);

            if (insidePolygon) {
                // Render the shield with sweep animation
                FragColor = mainShield(pixelPos);
            } else {
                // Point is outside the shield polygon
                discard;
            }
        }
    """.trimIndent()

    init {
        init()
        initShaders()
    }

    private fun init() {
        // Create VAO
        vao = glGenVertexArrays()
        glBindVertexArray(vao)

        // Create VBO - initially with full-screen quad, will be updated with bounding box
        // Each vertex now has: NDC coords (2 floats) + world coords (2 floats) = 4 floats per vertex
        val vertices = floatArrayOf(
            // NDC coords (location 0) + world coords (location 1)
            -1.0f, -1.0f, 0.0f, 0.0f,  // Bottom-left
            1.0f, -1.0f, 0.0f, 0.0f,   // Bottom-right
            1.0f, 1.0f, 0.0f, 0.0f,    // Top-right
            -1.0f, 1.0f, 0.0f, 0.0f    // Top-left
        )

        vbo = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, vbo)

        // Convert float array to FloatBuffer
        val buffer = BufferUtils.createFloatBuffer(vertices.size)
        buffer.put(vertices)
        buffer.flip()

        glBufferData(GL_ARRAY_BUFFER, buffer, GL_DYNAMIC_DRAW) // Changed to DYNAMIC_DRAW for updates

        // Set up vertex attributes
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * 4, 0)        // NDC coordinates
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * 4, 2 * 4)    // World coordinates
        glEnableVertexAttribArray(0)
        glEnableVertexAttribArray(1)

        glBindVertexArray(0)

        // No UBO needed - using direct uniforms instead
    }

    private fun updateQuadVertices() {
        if (boundingBoxWidth <= 0f || boundingBoxHeight <= 0f) {
            // Fallback to full-screen quad if bounding box is invalid
            val vertices = floatArrayOf(
                // NDC coords (location 0) + world coords (location 1)
                -1.0f, -1.0f, 0.0f, 0.0f,  // Bottom-left
                1.0f, -1.0f, 0.0f, 0.0f,   // Bottom-right
                1.0f, 1.0f, 0.0f, 0.0f,    // Top-right
                -1.0f, 1.0f, 0.0f, 0.0f    // Top-left
            )
            updateVBO(vertices)
            return
        }

        // Convert screen coordinates to NDC coordinates
        val left = (boundingBoxMinX / screenWidth) * 2.0f - 1.0f
        val right = (boundingBoxMaxX / screenWidth) * 2.0f - 1.0f
        val bottom = (boundingBoxMinY / screenHeight) * 2.0f - 1.0f
        val top = (boundingBoxMaxY / screenHeight) * 2.0f - 1.0f

        // Use dummy world coordinates (0,0) for all vertices
        // The vertex shader will handle the transformation
        val vertices = floatArrayOf(
            // NDC coords (location 0) + world coords (location 1)
            left, bottom, 0.0f, 0.0f,   // Bottom-left
            right, bottom, 0.0f, 0.0f,  // Bottom-right
            right, top, 0.0f, 0.0f,     // Top-right
            left, top, 0.0f, 0.0f       // Top-left
        )

        updateVBO(vertices)
    }

    private fun updateVBO(vertices: FloatArray) {
        glBindBuffer(GL_ARRAY_BUFFER, vbo)

        // Convert float array to FloatBuffer
        val buffer = BufferUtils.createFloatBuffer(vertices.size)
        buffer.put(vertices)
        buffer.flip()

        // Update the buffer data
        glBufferSubData(GL_ARRAY_BUFFER, 0, buffer)

        glBindBuffer(GL_ARRAY_BUFFER, 0)
    }

    // UBO initialization removed - using direct uniforms instead

    /**
     * Calculate the maximum number of points that could be generated after smoothing
     * This ensures the shader array is large enough to handle all possible smoothed points
     */
    private fun calculateMaxPointsAfterSmoothing(originalPointCount: Int): Int {
        val maxPoints = when (smoothingMethod) {
            SmoothingMethod.SPLINE -> {
                // Spline smoothing: Laplacian smoothing - no new points, just repositioned
                originalPointCount
            }

            SmoothingMethod.SIMPLE -> {
                // Simple smoothing: edge smoothing - no new points, just repositioned
                originalPointCount
            }

            SmoothingMethod.NONE -> {
                // Minimal smoothing: very subtle smoothing - no new points, just repositioned
                originalPointCount
            }
        }

        // Ensure we don't exceed reasonable bounds (safety check)
        val finalResult = minOf(maxPoints, 1000) // Maximum 1000 points for shader array
        return finalResult
    }

    private fun setPolygonPointUniforms(pointCount: Int) {
        // Set polygon points directly as uniforms instead of using UBO
        val actualPointCount = polygonPoints.size / 2

        // Safety check: ensure polygonPoints has even number of elements
        if (polygonPoints.size % 2 != 0) {
            Global.getLogger(this.javaClass).warn("polygonPoints has odd number of elements: ${polygonPoints.size}")
        }

        // Safety check: ensure we don't exceed the shader array size
        val safePointCount = minOf(actualPointCount, pointCount)

        // Set actual polygon points as uniforms (now world coordinates)
        for (i in 0 until safePointCount) {
            val x = polygonPoints[i * 2]
            val y = polygonPoints[i * 2 + 1]
            val uniformLocation = glGetUniformLocation(shaderProgram, "points[$i]")
            glUniform2f(uniformLocation, x, y)
        }

        // Set remaining array elements to zero
        for (i in safePointCount until pointCount) {
            val uniformLocation = glGetUniformLocation(shaderProgram, "points[$i]")
            glUniform2f(uniformLocation, 0.0f, 0.0f)
        }

        // Check for OpenGL errors
        val error = glGetError()
        if (error != GL_NO_ERROR) {
            Global.getLogger(this.javaClass).error("OpenGL error after setting uniforms: $error")
        }
    }

    private fun initShaders() {
        // Create and compile vertex shader
        val vertexShader = glCreateShader(GL_VERTEX_SHADER)
        glShaderSource(vertexShader, vertexShaderSource)
        glCompileShader(vertexShader)

        // Check for vertex shader compilation errors
        if (glGetShaderi(vertexShader, GL_COMPILE_STATUS) == GL_FALSE) {
            val error = glGetShaderInfoLog(vertexShader, 1024)
            throw RuntimeException("Vertex shader compilation failed: $error")
        }

        // Calculate the maximum number of points after smoothing
        val maxPointsAfterSmoothing = calculateMaxPointsAfterSmoothing(boundsCount)

        // Create and compile fragment shader
        val fragmentShader = glCreateShader(GL_FRAGMENT_SHADER)
        glShaderSource(
            fragmentShader,
            fragmentShaderSource.replace("POINT_COUNT_PLACEHOLDER", maxPointsAfterSmoothing.toString())
        )
        glCompileShader(fragmentShader)

        // Check for fragment shader compilation errors
        if (glGetShaderi(fragmentShader, GL_COMPILE_STATUS) == GL_FALSE) {
            val error = glGetShaderInfoLog(fragmentShader, 1024)
            throw RuntimeException("Fragment shader compilation failed: $error")
        }

        // Create shader program
        shaderProgram = glCreateProgram()
        glAttachShader(shaderProgram, vertexShader)
        glAttachShader(shaderProgram, fragmentShader)
        glLinkProgram(shaderProgram)

        // Check for linking errors
        if (glGetProgrami(shaderProgram, GL_LINK_STATUS) == GL_FALSE) {
            val error = glGetProgramInfoLog(shaderProgram, 1024)
            throw RuntimeException("Shader program linking failed: $error")
        }

        // Clean up shaders
        glDeleteShader(vertexShader)
        glDeleteShader(fragmentShader)
    }

    private fun calculateBoundingBox() {
        if (polygonPoints.size < 6) {
            boundingBoxMinX = 0f
            boundingBoxMaxX = 0f
            boundingBoxMinY = 0f
            boundingBoxMaxY = 0f
            boundingBoxWidth = 0f
            boundingBoxHeight = 0f
            return
        }

        // Calculate screen-space bounding box of the shield polygon
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        // polygonPoints now contains screen coordinates
        for (i in polygonPoints.indices step 2) {
            val screenX = polygonPoints[i]
            val screenY = polygonPoints[i + 1]

            minX = minOf(minX, screenX)
            maxX = maxOf(maxX, screenX)
            minY = minOf(minY, screenY)
            maxY = maxOf(maxY, screenY)
        }

        // Add margin for border effects and safety
        val margin = maxOf(borderWidth, glowWidth) + 10f
        boundingBoxMinX = minX - margin
        boundingBoxMaxX = maxX + margin
        boundingBoxMinY = minY - margin
        boundingBoxMaxY = maxY + margin
        boundingBoxWidth = boundingBoxMaxX - boundingBoxMinX
        boundingBoxHeight = boundingBoxMaxY - boundingBoxMinY
    }

    fun updatePolygon(newPoints: List<Vector2f>, viewport: ViewportAPI, scale: Vector2f = Vector2f(1.0f, 1.0f)) {
        scaleX = scale.x
        scaleY = scale.y

        // Get viewport scale for world-to-screen conversion
        viewportScaleX = viewport.viewMult
        viewportScaleY = viewport.viewMult

        // Validate input points
        if (newPoints.size < 3) { // Need at least 3 points (6 coordinates)
            Global.getLogger(this.javaClass).warn("Invalid polygon - need at least 3 points, got ${newPoints.size / 2}")
            polygonPoints = listOf()
            return
        }

        // Store world coordinates directly - transformations will be done in vertex shader
        val worldPoints = mutableListOf<Float>()
        for (segment in newPoints) {
            worldPoints.add(segment.x)
            worldPoints.add(segment.y)
        }

        // Apply smoothing to world coordinates first
        val smoothedWorldPoints = when (smoothingMethod) {
            SmoothingMethod.SPLINE -> if (enableSplineSmoothing) {
                subdividePointsWithSplines(worldPoints, splineSubdivisions)
            } else {
                worldPoints
            }

            SmoothingMethod.SIMPLE -> {
                smoothEdgesSimple(worldPoints, splineSubdivisions)
            }

            SmoothingMethod.NONE -> {
                smoothEdgesMinimal(worldPoints, 2) // Minimal curve smoothing
            }
        }

        // Convert world points to screen coordinates for the fragment shader
        val screenPoints = mutableListOf<Float>()
        var sweepMinX = Float.MAX_VALUE
        var sweepMaxX = Float.MIN_VALUE

        // Get ship center in screen coordinates for consistent calculations
        val shipScreenX = viewport.convertWorldXtoScreenX(ship.location.x)
        val shipScreenY = viewport.convertWorldYtoScreenY(ship.location.y)

        for (i in smoothedWorldPoints.indices step 2) {
            val worldPoint = Vector2f(smoothedWorldPoints[i], smoothedWorldPoints[i + 1])
            val scaledPoint = Vector2f(worldPoint.x * scaleX, worldPoint.y * scaleY)
            val translatedPoint = Vector2f(scaledPoint.x + ship.location.x, scaledPoint.y + ship.location.y)
            val rotatedPoint = Utils.rotatePointByDegrees(translatedPoint, ship.location, ship.facing)

            // Convert to screen coordinates using viewport
            val screenX = viewport.convertWorldXtoScreenX(rotatedPoint.x)
            val screenY = viewport.convertWorldYtoScreenY(rotatedPoint.y)

            // Calculate ship-relative coordinates in screen space for sweep bounds
            val screenRelPoint = Vector2f(screenX - shipScreenX, screenY - shipScreenY)
            val facingRad = Math.toRadians(ship.facing.toDouble()).toFloat()
            val cosFacing = kotlin.math.cos(facingRad)
            val sinFacing = kotlin.math.sin(facingRad)
            val shipRelX = screenRelPoint.x * cosFacing + screenRelPoint.y * sinFacing

            sweepMinX = minOf(sweepMinX, shipRelX)
            sweepMaxX = maxOf(sweepMaxX, shipRelX)

            screenPoints.add(screenX)
            screenPoints.add(screenY)
        }

        // Calculate sweep position using the bounds we just calculated
        val sweepWidth = sweepMaxX - sweepMinX
        sweepPosition = sweepMaxX - (ship.phaseCloak?.effectLevel ?: 1f) * sweepWidth

        // Store the screen coordinates for the fragment shader
        polygonPoints = screenPoints

        // Shield bounds are now calculated inline above

        // Calculate bounding box for rendering optimization
        calculateBoundingBox()

        // Update quad vertices to match bounding box
        updateQuadVertices()
    }

    private fun subdividePointsWithSplines(points: List<Float>, subdivisions: Int = 8): List<Float> {
        val result = mutableListOf<Float>()
        val numPoints = points.size / 2

        if (numPoints < 3) return points.toList() // Need at least 3 points for a polygon

        // Convert to pairs for easier handling
        val pairs = points.chunked(2) { Pair(it[0], it[1]) }

        // Apply Laplacian smoothing to existing points (no new points added)
        val smoothedPoints = laplacianSmoothing(pairs, subdivisions)

        // Return smoothed points as flat list
        for (point in smoothedPoints) {
            result.add(point.first)
            result.add(point.second)
        }

        return result
    }

    private fun laplacianSmoothing(points: List<Pair<Float, Float>>, iterations: Int): List<Pair<Float, Float>> {
        if (points.size < 3) return points

        var currentPoints = points.toMutableList()

        for (iteration in 0 until iterations) {
            val smoothedPoints = mutableListOf<Pair<Float, Float>>()

            for (i in currentPoints.indices) {
                val prev = currentPoints[(i - 1 + currentPoints.size) % currentPoints.size]
                val current = currentPoints[i]
                val next = currentPoints[(i + 1) % currentPoints.size]

                // Laplacian smoothing: move point towards the average of its neighbors
                val smoothingFactor = 0.3f // Controls how much smoothing is applied
                val smoothedX = current.first + smoothingFactor * ((prev.first + next.first) / 2f - current.first)
                val smoothedY = current.second + smoothingFactor * ((prev.second + next.second) / 2f - current.second)

                smoothedPoints.add(Pair(smoothedX, smoothedY))
            }

            currentPoints = smoothedPoints
        }

        return currentPoints
    }

    fun updateColor(r: Int, g: Int, b: Int, a: Int) {
        colorRGBA = floatArrayOf(
            r / 255f,
            g / 255f,
            b / 255f,
            a / 100f
        )
    }

    fun updateZoomLevel(zoom: Float) {
        zoomLevel = zoom
    }

    fun updateScreenResolution(width: Float, height: Float) {
        screenWidth = width
        screenHeight = height
        // Recalculate quad vertices when screen resolution changes
        if (boundingBoxWidth > 0f && boundingBoxHeight > 0f) {
            updateQuadVertices()
        }
    }

    fun updateShipPosition() {
        // Don't recalculate bounding box here - it should be calculated with viewport in updatePolygon
        // Just update rotation matrix when ship moves
        updateRotationMatrix()
    }

    private fun updateRotationMatrix() {
        // Calculate rotation matrix for ship facing
        val facingRad = Math.toRadians(ship.facing.toDouble()).toFloat()
        val cosFacing = kotlin.math.cos(facingRad)
        val sinFacing = kotlin.math.sin(facingRad)

        // 2x2 rotation matrix: [cos -sin; sin cos]
        shipRotationMatrix[0] = cosFacing
        shipRotationMatrix[1] = -sinFacing
        shipRotationMatrix[2] = sinFacing
        shipRotationMatrix[3] = cosFacing
    }

    fun setDebugMode(enabled: Boolean) {
        debugMode = enabled
    }

    fun setBoundingBoxOptimization(enabled: Boolean) {
        enableBoundingBoxOptimization = enabled
    }

    fun setSplineSubdivisions(subdivisions: Int) {
        splineSubdivisions = subdivisions.coerceIn(1, 8) // Limit to reasonable range
    }

    fun getSplineSubdivisions(): Int {
        return splineSubdivisions
    }

    fun getSmoothingMethod(): SmoothingMethod {
        return smoothingMethod
    }

    // Alternative smoothing method: simple edge smoothing by repositioning points
    private fun smoothEdgesSimple(points: List<Float>, subdivisions: Int = 2): List<Float> {
        val result = mutableListOf<Float>()
        val numPoints = points.size / 2

        if (numPoints < 3) return points.toList()

        // Convert to pairs for easier handling
        val pairs = points.chunked(2) { Pair(it[0], it[1]) }

        // Apply simple smoothing by moving points towards edge midpoints
        val smoothedPoints = simpleEdgeSmoothing(pairs, subdivisions)

        // Return smoothed points as flat list
        for (point in smoothedPoints) {
            result.add(point.first)
            result.add(point.second)
        }

        return result
    }

    private fun simpleEdgeSmoothing(points: List<Pair<Float, Float>>, iterations: Int): List<Pair<Float, Float>> {
        if (points.size < 3) return points

        var currentPoints = points.toMutableList()

        for (iteration in 0 until iterations) {
            val smoothedPoints = mutableListOf<Pair<Float, Float>>()

            for (i in currentPoints.indices) {
                val current = currentPoints[i]
                val next = currentPoints[(i + 1) % currentPoints.size]

                // Move point slightly towards the midpoint of the edge
                val smoothingFactor = 0.2f // Gentle smoothing
                val edgeMidX = (current.first + next.first) / 2f
                val edgeMidY = (current.second + next.second) / 2f

                val smoothedX = current.first + smoothingFactor * (edgeMidX - current.first)
                val smoothedY = current.second + smoothingFactor * (edgeMidY - current.second)

                smoothedPoints.add(Pair(smoothedX, smoothedY))
            }

            currentPoints = smoothedPoints
        }

        return currentPoints
    }

    // Minimal smoothing method that creates very subtle shape smoothing
    private fun smoothEdgesMinimal(points: List<Float>, subdivisions: Int = 2): List<Float> {
        val result = mutableListOf<Float>()
        val numPoints = points.size / 2

        if (numPoints < 3) return points.toList()

        // Convert to pairs for easier handling
        val pairs = points.chunked(2) { Pair(it[0], it[1]) }

        // Apply very subtle smoothing by slightly adjusting point positions
        val smoothedPoints = minimalSmoothing(pairs, subdivisions)

        // Return smoothed points as flat list
        for (point in smoothedPoints) {
            result.add(point.first)
            result.add(point.second)
        }

        return result
    }

    private fun minimalSmoothing(points: List<Pair<Float, Float>>, iterations: Int): List<Pair<Float, Float>> {
        if (points.size < 3) return points

        var currentPoints = points.toMutableList()

        for (iteration in 0 until iterations) {
            val smoothedPoints = mutableListOf<Pair<Float, Float>>()

            for (i in currentPoints.indices) {
                val prev = currentPoints[(i - 1 + currentPoints.size) % currentPoints.size]
                val current = currentPoints[i]
                val next = currentPoints[(i + 1) % currentPoints.size]

                // Very subtle smoothing: move point slightly towards the average of neighbors
                val smoothingFactor = 0.1f // Very gentle smoothing
                val avgX = (prev.first + next.first) / 2f
                val avgY = (prev.second + next.second) / 2f

                val smoothedX = current.first + smoothingFactor * (avgX - current.first)
                val smoothedY = current.second + smoothingFactor * (avgY - current.second)

                smoothedPoints.add(Pair(smoothedX, smoothedY))
            }

            currentPoints = smoothedPoints
        }

        return currentPoints
    }

    fun updateBorder(r: Int, g: Int, b: Int, a: Int, width: Float, blend: Float) {
        borderColorRGBA = floatArrayOf(
            r / 255f,
            g / 255f,
            b / 255f,
            a / 100f
        )
        borderWidth = width
    }

    fun updateGlow(intensity: Float, width: Float) {
        glowIntensity = intensity.coerceIn(0f, 1f)
        glowWidth = width
    }

    fun startRipple(x: Float, y: Float, width: Float, r: Int, g: Int, b: Int, a: Int, intensity: Float = 1f) {
        val ripple = Ripple(
            center = Pair(x, y),
            radius = 1f,
            width = width,
            color = floatArrayOf(r / 255f, g / 255f, b / 255f, a / 100f),
            time = 0f,
            intensity = intensity.coerceIn(0f, 1f),
            maxDistance = 1000f * intensity.coerceIn(0.2f, 1f)
        )
        activeRipples.add(ripple)
    }

    fun updateRipple(deltaTime: Float, speed: Float = 400f) {
        activeRipples.removeAll { ripple ->
            ripple.time += deltaTime
            // Scale speed inversely with viewport scale to maintain consistent visual speed
            val visualSpeed = speed / viewportScaleX
            ripple.radius += visualSpeed * deltaTime
            ripple.radius > ripple.maxDistance
        }
    }

    fun setHoneycombEnabled(enabled: Boolean) {
        honeycombEnabled = enabled
    }

    fun triggerShieldPop(intensity: Float = 1.0f) {
        // Create darker colors for the cloud effect
        val darkColor = floatArrayOf(colorRGBA[0] * 0.4f, colorRGBA[1] * 0.4f, colorRGBA[2] * 0.4f, 1f)
        val darkerColor =
            floatArrayOf(colorRGBA[0] * 0.25f, colorRGBA[1] * 0.25f, colorRGBA[2] * 0.25f, 0.8f)

        // Generate random radial lines
        val lines = mutableListOf<RadialLine>()
        var currentAngle = kotlin.random.Random.nextFloat() * 2f * Math.PI.toFloat() // Random start angle

        // Parameters for the lines
        val minLineLength = 0.2f * (0.7f + intensity * 0.3f)  // Reduced by half from previous value
        val maxLineLength = 0.45f * (0.7f + intensity * 0.3f)  // Reduced by half from previous value
        val minStartOffset = 0.5f  // Lines start 50% away from center
        val maxStartOffset = 0.7f  // Lines start up to 70% away from center
        val baseThickness = 2f + intensity * 2f // Base thickness in pixels
        val minAngularSpacing = Math.toRadians(10.0).toFloat()
        val maxAngularSpacing = Math.toRadians(40.0).toFloat()
        val numLinesMultiplier = 1f + intensity * 0.5f

        // Generate lines with random spacing until we go all the way around
        val startAngle = currentAngle
        while (currentAngle - startAngle < Math.PI * 2) {
            // Random length for this line
            val length = minLineLength + kotlin.random.Random.nextFloat() * (maxLineLength - minLineLength)

            // Random start offset from center
            val startOffset = minStartOffset + kotlin.random.Random.nextFloat() * (maxStartOffset - minStartOffset)

            // Random thickness variation
            val thickness = baseThickness * (0.7f + kotlin.random.Random.nextFloat() * 0.6f)

            // Random alpha
            val alpha = 0.7f + kotlin.random.Random.nextFloat() * 0.3f

            // Random speed multiplier
            val speedMultiplier = 0.85f + kotlin.random.Random.nextFloat() * 0.3f

            lines.add(
                RadialLine(
                    angle = currentAngle,
                    length = length,
                    startOffset = startOffset,
                    thickness = thickness,
                    alpha = alpha,
                    speedMultiplier = speedMultiplier
                )
            )

            // Calculate next angle with random spacing
            val spacing = minAngularSpacing + kotlin.random.Random.nextFloat() * (maxAngularSpacing - minAngularSpacing)
            currentAngle += spacing / numLinesMultiplier
        }

        // Create the pop wave with the radial lines
        activePopWave = PopWave(
            time = 0f,
            maxTime = 0.3f,
            color = darkColor,
            darkerColor = darkerColor,
            thickness = 20f * (0.5f + intensity * 0.5f),
            startScale = 0.1f,
            endScale = 3.0f + intensity * 0.5f,
            intensity = intensity.coerceIn(0.1f, 1.5f),
            radialLines = lines
        )
    }

    fun updateShieldPop(deltaTime: Float) {
        activePopWave?.let { popWave ->
            popWave.time += deltaTime

            // Remove the pop wave if it's completed its animation
            if (popWave.time >= popWave.maxTime) {
                activePopWave = null
            }
        }
    }

    fun updateTime(amount: Float) {
        this.time += amount
    }

    fun isPopWaveActive(): Boolean {
        return activePopWave !== null
    }

    fun drawShieldPop(): Boolean {
        val isDrawing = activePopWave != null

        return isDrawing
    }

    fun updateShieldHPPercent(percent: Float) {
        shieldHPPercent = percent
    }

    fun drawShield(viewport: ViewportAPI) {
        if (polygonPoints.isEmpty()) return

        // Update viewport scale for ripple calculations
        viewportScaleX = viewport.viewMult
        viewportScaleY = viewport.viewMult

        // Validate polygon before rendering
        if (polygonPoints.size < 6) {
            Global.getLogger(this.javaClass)
                .warn("Cannot render polygon with ${polygonPoints.size / 2} points (minimum 3 required)")
            return
        }

        // Ensure bounding box is up-to-date before rendering (if optimization is enabled)
        if (enableBoundingBoxOptimization) {
            // Don't recalculate bounding box here - it should already be calculated with viewport in updatePolygon
            // Just update quad vertices if needed
            if (boundingBoxWidth > 0f && boundingBoxHeight > 0f) {
                updateQuadVertices()
            }
        }

        // Ensure rotation matrix is up-to-date
        updateRotationMatrix()

        // Save current OpenGL state
        glPushAttrib(GL_ALL_ATTRIB_BITS)
        glPushMatrix()

        // Save current OpenGL state variables
        val currentProgram = glGetInteger(GL_CURRENT_PROGRAM)
        val currentVAO = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        val currentVBO = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        val currentTexture = glGetInteger(GL_TEXTURE_BINDING_2D)
        val blendEnabled = glIsEnabled(GL_BLEND)
        val depthTestEnabled = glIsEnabled(GL_DEPTH_TEST)
        val stencilTestEnabled = glIsEnabled(GL_STENCIL_TEST)
        val texture2DEnabled = glIsEnabled(GL_TEXTURE_2D)
        val scissorTestEnabled = glIsEnabled(GL_SCISSOR_TEST)

        try {
            // Scissor test disabled for debugging - only using bounding box quad optimization
            // The bounding box optimization is handled by the dynamic quad vertices

            // Use our shader program
            glUseProgram(shaderProgram)

            // Bind VAO
            glBindVertexArray(vao)

            // Set uniforms
            glUniform4f(
                glGetUniformLocation(shaderProgram, "shieldColor"),
                colorRGBA[0], colorRGBA[1], colorRGBA[2], colorRGBA[3]
            )
            glUniform4f(
                glGetUniformLocation(shaderProgram, "borderColor"),
                borderColorRGBA[0], borderColorRGBA[1], borderColorRGBA[2], borderColorRGBA[3]
            )
            glUniform1f(glGetUniformLocation(shaderProgram, "borderWidth"), borderWidth)
            glUniform1f(glGetUniformLocation(shaderProgram, "glowIntensity"), glowIntensity)
            glUniform1f(glGetUniformLocation(shaderProgram, "glowWidth"), glowWidth)
            glUniform1f(glGetUniformLocation(shaderProgram, "chargeProgress"), ship.phaseCloak?.effectLevel ?: 1f)
            glUniform1f(glGetUniformLocation(shaderProgram, "shieldSweepProgress"), ship.phaseCloak?.effectLevel ?: 1f)
            glUniform1f(glGetUniformLocation(shaderProgram, "sweepEdgeWidth"), 10.0f)
            glUniform1f(glGetUniformLocation(shaderProgram, "shieldHPPercent"), shieldHPPercent)
            glUniform1f(glGetUniformLocation(shaderProgram, "shipFacing"), ship.facing)

            // Set sweep position for fragment shader
            glUniform1f(glGetUniformLocation(shaderProgram, "sweepPosition"), sweepPosition)
            // Pass ship location in screen coordinates to match sweep bounds calculation
            val shipScreenX = viewport.convertWorldXtoScreenX(ship.location.x)
            val shipScreenY = viewport.convertWorldYtoScreenY(ship.location.y)
            glUniform2f(glGetUniformLocation(shaderProgram, "center"), shipScreenX, shipScreenY)
            glUniform1f(glGetUniformLocation(shaderProgram, "time"), time)

            // Set vertex shader uniforms for optimization
            glUniform2f(glGetUniformLocation(shaderProgram, "resolution"), screenWidth, screenHeight)
            glUniform2f(glGetUniformLocation(shaderProgram, "scale"), scaleX, scaleY)
            glUniform4f(
                glGetUniformLocation(shaderProgram, "shipRotationMatrix"),
                shipRotationMatrix[0], shipRotationMatrix[1], shipRotationMatrix[2], shipRotationMatrix[3]
            )
            glUniform2f(glGetUniformLocation(shaderProgram, "viewportScale"), viewportScaleX, viewportScaleY)

            // Set honeycomb uniforms
            glUniform1f(glGetUniformLocation(shaderProgram, "honeycombSize"), honeycombSize)
            glUniform1f(glGetUniformLocation(shaderProgram, "edgeWidth"), edgeWidth)

            glUniform1i(glGetUniformLocation(shaderProgram, "honeycombEnabled"), if (honeycombEnabled) 1 else 0)
            // Scale uniform is already set above for vertex shader
            glUniform1f(
                glGetUniformLocation(shaderProgram, "rotation"),
                Math.toRadians(ship.facing.toDouble()).toFloat()
            )

            // Set ripple uniforms
            glUniform1i(glGetUniformLocation(shaderProgram, "numRipples"), activeRipples.size)
            for (i in activeRipples.indices.take(10)) {
                val ripple = activeRipples[i]
                glUniform4f(
                    glGetUniformLocation(shaderProgram, "rippleColors[$i]"),
                    ripple.color[0], ripple.color[1], ripple.color[2], ripple.color[3]
                )
                // Convert ripple center to screen coordinates
                val rippleScreenX = viewport.convertWorldXtoScreenX(ripple.center.first)
                val rippleScreenY = viewport.convertWorldYtoScreenY(ripple.center.second)
                glUniform2f(
                    glGetUniformLocation(shaderProgram, "rippleCenters[$i]"),
                    rippleScreenX, rippleScreenY
                )
                // Convert ripple radius to screen coordinates (same as centers)
                val rippleScreenRadius = ripple.radius * viewport.viewMult
                glUniform1f(glGetUniformLocation(shaderProgram, "rippleRadii[$i]"), rippleScreenRadius)
                glUniform1f(glGetUniformLocation(shaderProgram, "rippleIntensities[$i]"), ripple.intensity)
                // Convert ripple width to screen coordinates (same as centers)
                val rippleScreenWidth = ripple.width * viewport.viewMult
                glUniform1f(glGetUniformLocation(shaderProgram, "rippleWidths[$i]"), rippleScreenWidth)
            }

            // Set pop wave uniforms
            glUniform1i(
                glGetUniformLocation(shaderProgram, "popWaveActive"),
                if (activePopWave != null) 1 else 0
            )
            activePopWave?.let { popWave ->
                glUniform1f(glGetUniformLocation(shaderProgram, "popWaveTime"), popWave.time)
                glUniform1f(glGetUniformLocation(shaderProgram, "popWaveMaxTime"), popWave.maxTime)
                glUniform4f(
                    glGetUniformLocation(shaderProgram, "popWaveColor"),
                    popWave.color[0], popWave.color[1], popWave.color[2], popWave.color[3]
                )
                glUniform4f(
                    glGetUniformLocation(shaderProgram, "popWaveDarkerColor"),
                    popWave.darkerColor[0], popWave.darkerColor[1], popWave.darkerColor[2], popWave.darkerColor[3]
                )
                glUniform1f(glGetUniformLocation(shaderProgram, "popWaveThickness"), popWave.thickness)
                glUniform1f(glGetUniformLocation(shaderProgram, "popWaveStartScale"), popWave.startScale)
                glUniform1f(glGetUniformLocation(shaderProgram, "popWaveEndScale"), popWave.endScale)
                glUniform1f(glGetUniformLocation(shaderProgram, "popWaveIntensity"), popWave.intensity)
                glUniform1f(glGetUniformLocation(shaderProgram, "popEffectRadius"), popEffectRadius)
            }

            // Resolution uniform is already set above for vertex shader

            // Set zoom level uniform
            glUniform1f(glGetUniformLocation(shaderProgram, "zoomLevel"), zoomLevel)

            // Set debug mode uniform
            glUniform1i(glGetUniformLocation(shaderProgram, "debugMode"), if (debugMode) 1 else 0)

            // Set bounding box uniforms for debug visualization
            glUniform2f(glGetUniformLocation(shaderProgram, "boundingBoxMin"), boundingBoxMinX, boundingBoxMinY)
            glUniform2f(glGetUniformLocation(shaderProgram, "boundingBoxMax"), boundingBoxMaxX, boundingBoxMaxY)

            // Convert polygon points to screen coordinates and pass to shader
            // Calculate the maximum number of points the shader can handle after smoothing
            val maxShaderPoints = calculateMaxPointsAfterSmoothing(boundsCount)

            // Set polygon points directly as uniforms
            setPolygonPointUniforms(maxShaderPoints)

            // Set debug polygon uniform
            glUniform1i(glGetUniformLocation(shaderProgram, "debugPolygon"), if (debugMode) 1 else 0)

            // Enable blending for transparency
            glEnable(GL_BLEND)
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

            // Draw full-screen quad to render the points
            glDrawArrays(GL_TRIANGLE_FAN, 0, 4)

        } finally {
            // Restore OpenGL state variables
            glUseProgram(currentProgram)
            glBindVertexArray(currentVAO)
            glBindBuffer(GL_ARRAY_BUFFER, currentVBO)
            glBindTexture(GL_TEXTURE_2D, currentTexture)

            // Restore scissor test state
            if (scissorTestEnabled) {
                glEnable(GL_SCISSOR_TEST)
            } else {
                glDisable(GL_SCISSOR_TEST)
            }

            // Restore enabled states
            if (blendEnabled) glEnable(GL_BLEND) else glDisable(GL_BLEND)
            if (depthTestEnabled) glEnable(GL_DEPTH_TEST) else glDisable(GL_DEPTH_TEST)
            if (stencilTestEnabled) glEnable(GL_STENCIL_TEST) else glDisable(GL_STENCIL_TEST)
            if (texture2DEnabled) glEnable(GL_TEXTURE_2D) else glDisable(GL_TEXTURE_2D)

            // Restore OpenGL state
            glPopMatrix()
            glPopAttrib()
        }
    }

    fun cleanup() {
        try {
            // Clean up OpenGL resources
            if (vbo != 0) {
                glDeleteBuffers(vbo)
                vbo = 0
            }
            if (vao != 0) {
                glDeleteVertexArrays(vao)
                vao = 0
            }
            if (shaderProgram != 0) {
                glDeleteProgram(shaderProgram)
                shaderProgram = 0
            }
        } catch (e: Exception) {
            Global.getLogger(this.javaClass).error("Error during cleanup: ${e.message}")
        }
    }
}
