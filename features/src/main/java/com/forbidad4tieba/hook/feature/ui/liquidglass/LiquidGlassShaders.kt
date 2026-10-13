// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
// Shader source and adaptation details: app/src/main/assets/open-source-licenses.txt.
package com.forbidad4tieba.hook.feature.ui.liquidglass

internal object LiquidGlassShaders {
    private const val MAX_BLUR_TAPS = 7
    fun blur(tapCount: Int): String {
    require(tapCount in 1..MAX_BLUR_TAPS)
    return """
    uniform shader child;
    uniform float2 in_blurOffset[$tapCount];
    uniform float in_blurWeight[$tapCount];
    uniform float2 in_maxCoord;

    half4 main(float2 xy) {
        half4 color = half4(0);
        for (int i = 0; i < $tapCount; i++) {
            float2 offset = in_blurOffset[i];
            float2 c1 = clamp(xy + offset, float2(0.5), in_maxCoord);
            float2 c2 = clamp(xy - offset, float2(0.5), in_maxCoord);
            color += (child.eval(c1) + child.eval(c2)) * half(in_blurWeight[i]);
        }
        if (color.a > 0.0039) {
            return half4(color.rgb / color.a, 1.0);
        }
        return color;
    }
"""
    }

    const val LENS = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;


float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}


float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));

    float2 refractedCoord = coord + d * grad;
    return content.eval(refractedCoord);
}
"""

    const val DISPERSION_LENS = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;


float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}


float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));

    float2 refractedCoord = coord + d * grad;
    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;

    half4 color = half4(0.0);

    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;

    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;

    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;

    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;

    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;

    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;

    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;

    return color;
}
"""

    const val BLOOM = """
uniform float2 halfView;
uniform float2 halfViewFloor;
uniform float4 cornerRadii;
uniform float strokeWidth;
uniform float innerBlurRadius;
uniform float innerBlurRadiusSq;
uniform float highlightAlpha;

layout(color) uniform half4 strokeColor;
uniform float strokeAlphaMul;

uniform float3 lightDir1;
layout(color) uniform half4 lightColor1;
uniform float lightIntensity1;

uniform float3 lightDir2;
layout(color) uniform half4 lightColor2;
uniform float lightIntensity2;

float pickRadius(float2 fragCoord, float4 radii) {
    float2 up = fragCoord.y < halfView.y ? radii.xy : radii.zw;
    return fragCoord.x < halfView.x ? up.x : up.y;
}

// caller passes non-negative pos (already abs-folded), so skip the redundant abs.
float roundedBoxSDF(float2 pos, float2 halfSize, float radius) {
    radius = min(radius, min(halfSize.x, halfSize.y));
    float2 d = pos - halfSize + radius;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - radius;
}

float3 getNormal(float2 fragCoord, float sdf, float R) {
    float2 xy = fragCoord - halfViewFloor;
    float2 xy_a = abs(xy);
    float t = smoothstep(-innerBlurRadius, 0.0, sdf);
    float z = sqrt(max(innerBlurRadiusSq - t * t, 0.0));
    float3 coord = float3(xy_a, -z);

    float2 corner = halfView - R;
    corner.x = min(corner.x, xy_a.x);
    corner.y = min(corner.y, xy_a.y);

    float2 dir = normalize(coord.xy - corner.xy);
    corner += dir * (R - innerBlurRadius);

    if (any(lessThan(xy_a, corner))) {
        return float3(0.0, 0.0, -1.0);
    }

    float2 signal = sign(xy);
    float3 n = normalize(coord - float3(corner, 0.0));
    n.xy *= signal;
    return n;
}

half4 main(float2 fragCoord) {
    float2 xy = abs(fragCoord - halfView);

    float originRadius = pickRadius(fragCoord, cornerRadii);
    float R = max(originRadius, innerBlurRadius);

    if (all(lessThan(xy, halfView - R))) {
        return half4(0.0);
    }

    float sdf = roundedBoxSDF(xy, halfView, originRadius);
    half outMask = half(smoothstep(0.0, -1.0, sdf));
    float strokeAlpha = smoothstep(-strokeWidth, -strokeWidth + 1.0, sdf);

    // Native: stroke = uStrokeColor * sa; result += stroke.rgb * stroke.a
    //       = strokeColor.rgb * strokeColor.a * sa^2
    half3 rgb = strokeColor.rgb * half(strokeAlphaMul * strokeAlpha * strokeAlpha);

    float3 n = getNormal(fragCoord, sdf, R);

    float l1 = dot(n.xy, lightDir1.xy);
    rgb += half(l1 * l1 * lightIntensity1) * lightColor1.rgb;
    float l2 = dot(n.xy, lightDir2.xy);
    rgb += half(l2 * l2 * lightIntensity2) * lightColor2.rgb;

    return half4(rgb * half(highlightAlpha), 1.0) * outMask;
}
"""

    const val GLOW = """
    layout(color) uniform half4 color;
    uniform float radius;
    uniform float2 position;

    half4 main(float2 coord) {
        float dist = distance(coord, position);
        float intensity = smoothstep(radius, radius * 0.5, dist);
        return color * intensity;
    }"""

    const val COLOR_CONTROLS = """
    uniform shader child;
    uniform float in_brightness;
    uniform float in_contrast;
    uniform float in_saturation;

    half4 main(float2 xy) {
        half4 src = child.eval(xy);
        half a = src.a;
        if (a < 0.001) return src;

        half3 c = src.rgb / a;

        if (in_brightness != 0.0) {
            c = pow(c, half3(2.2));
            if (in_brightness > 0.0) {
                c = mix(c, half3(1.0), half(in_brightness));
            } else {
                c = c * half(1.0 + in_brightness);
            }
            c = pow(c, half3(0.45454545));
        }

        if (in_contrast != 1.0) {
            c = (c - 0.5) * half(in_contrast) + 0.5;
        }

        if (in_saturation != 1.0) {
            half lum = dot(c, half3(0.2126, 0.7152, 0.0722));
            c = mix(half3(lum), c, half(in_saturation));
        }

        c = clamp(c, half3(0.0), half3(1.0));
        return half4(c * a, a);
    }
"""

    const val DOWNSAMPLE_2X = """
    uniform shader child;
    uniform float2 maxCoord;
    half4 main(float2 xy) {
        float2 minCoord = float2(0.5);
        half4 color = half4(0);
        color += child.eval(clamp(xy + float2(-0.25, -0.25), minCoord, maxCoord));
        color += child.eval(clamp(xy + float2( 0.25, -0.25), minCoord, maxCoord));
        color += child.eval(clamp(xy + float2(-0.25,  0.25), minCoord, maxCoord));
        color += child.eval(clamp(xy + float2( 0.25,  0.25), minCoord, maxCoord));
        return color * 0.25;
    }
"""

    const val DOWNSAMPLE_4X = """
    uniform shader child;
    uniform float2 maxCoord;
    half4 main(float2 xy) {
        float2 minCoord = float2(0.5);
        half4 color = half4(0);
        color += child.eval(clamp(xy + float2(-0.75, -0.75), minCoord, maxCoord));
        color += child.eval(clamp(xy + float2( 0.75, -0.75), minCoord, maxCoord));
        color += child.eval(clamp(xy + float2(-0.75,  0.75), minCoord, maxCoord));
        color += child.eval(clamp(xy + float2( 0.75,  0.75), minCoord, maxCoord));
        return color * 0.25;
    }
"""
}
