package com.asciilive.wallpaper.engine

object AsciiShaders {

    const val VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aTexCoord;

out vec2 vUv;

void main() {
    vUv = aTexCoord;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
"""

    const val FRAGMENT_SHADER = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require

precision highp float;

in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uGlyphAtlas;
uniform sampler2D uPhotoTex;
uniform samplerExternalOES uVideoTex;
uniform mat4 uVideoMatrix;

uniform vec2 uResolution;
uniform vec2 uMediaResolution;
uniform float uTime;

uniform int uSourceMode;      // 0 = Demo, 1 = Photo, 2 = Video
uniform int uDemoPreset;      // 0 = Fusilli, 1 = Fusilli XXL, 2 = Cubes, 3 = Cubes XXL, 4 = Big Cube, 5 = DNA Helix, 6 = Event Horizon
uniform int uScaleMode;       // 0 = Fill, 1 = Fit
uniform float uColumns;
uniform float uCharCount;

uniform int uColorMode;       // 0 = Monochrome, 1 = Original, 2 = Duotone, 3 = Material You
uniform vec3 uFgColor;
uniform vec3 uBgColor;
uniform float uInvert;
uniform float uBrightness;
uniform float uContrast;
uniform float uGlow;
uniform float uScanlines;

// --- Utility ---
float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

mat2 rot2(float a) {
    float s = sin(a);
    float c = cos(a);
    return mat2(c, -s, s, c);
}

float sdSegment(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-5), 0.0, 1.0);
    return length(pa - ba * h);
}

// --- Demo 0: Diagonal 3D DNA Double Helix (inspired by reference video) ---
vec3 demoDnaHelix(vec2 uv, float t) {
    vec2 p = (uv - 0.5) * vec2(uResolution.x / max(uResolution.y, 1.0), 1.0);
    // Rotate diagonally like the reference video
    vec2 q = rot2(-0.68) * p;
    float y = q.y * 5.2 + t * 1.15;
    float radius = 0.21;

    float x1 = sin(y) * radius;
    float z1 = cos(y);
    float x2 = sin(y + 3.14159265) * radius;
    float z2 = cos(y + 3.14159265);

    float d1 = abs(q.x - x1);
    float d2 = abs(q.x - x2);

    float depth1 = 0.55 + 0.45 * z1;
    float depth2 = 0.55 + 0.45 * z2;

    float strand1 = smoothstep(0.048, 0.008, d1) * depth1;
    float strand2 = smoothstep(0.048, 0.008, d2) * depth2;

    // Periodic base-pair rungs connecting both strands
    float rungPhase = fract(y * 0.65);
    float rungMask = smoothstep(0.14, 0.02, abs(rungPhase - 0.5));
    float minX = min(x1, x2);
    float maxX = max(x1, x2);
    float insideRung = step(minX, q.x) * step(q.x, maxX);
    float rung = rungMask * insideRung * 0.62;

    float intensity = clamp(max(max(strand1, strand2), rung), 0.0, 1.0);
    vec3 col = mix(vec3(0.75, 0.88, 1.0), vec3(1.0, 1.0, 1.0), intensity);
    return col * intensity;
}

// --- Demo 1: Event Horizon Black Hole + Accretion Disk + Starfield ---
vec3 demoEventHorizon(vec2 uv, float t) {
    vec2 p = (uv - 0.5) * vec2(uResolution.x / max(uResolution.y, 1.0), 1.0);
    float r = length(p);
    float angle = atan(p.y, p.x);

    // Event horizon shadow & sharp photon ring
    float shadow = smoothstep(0.165, 0.195, r);
    float photonRing = exp(-pow((r - 0.19) * 26.0, 2.0)) * 1.15;

    // Tilted elliptical accretion disk
    vec2 diskP = rot2(-0.14) * p;
    diskP.y *= 3.4;
    float diskR = length(diskP);
    float diskAngle = atan(diskP.y, diskP.x);
    float swirl = 0.55 + 0.45 * sin(diskAngle * 4.0 - t * 2.4 + diskR * 18.0);
    float diskBand = exp(-pow((diskR - 0.30) * 9.5, 2.0)) * swirl;
    // Front part of disk passes in front of black hole
    float frontMask = smoothstep(-0.04, 0.06, -diskP.y);
    float disk = diskBand * mix(shadow, 1.0, frontMask);

    // Gravitational lensing halo
    float halo = exp(-pow((r - 0.24) * 14.0, 2.0)) * (0.45 + 0.25 * sin(angle * 3.0 + t * 1.4)) * shadow;

    // Sparse procedural starfield
    vec2 starCell = floor((p + vec2(t * 0.012, -t * 0.006)) * 22.0);
    float starRnd = hash21(starCell);
    vec2 starLocal = fract((p + vec2(t * 0.012, -t * 0.006)) * 22.0) - 0.5;
    float star = step(0.955, starRnd) * smoothstep(0.22, 0.04, length(starLocal)) * shadow;
    star *= 0.6 + 0.4 * sin(t * 3.0 + starRnd * 40.0);

    float lum = clamp(photonRing + disk + halo + star * 0.85, 0.0, 1.0);
    vec3 warm = mix(vec3(0.85, 0.82, 0.98), vec3(1.0, 0.94, 0.84), clamp(disk + photonRing, 0.0, 1.0));
    return warm * lum;
}

// --- Demo 2: Rotating 3D Wireframe Cube ---
vec2 project3D(vec3 v, float ax, float ay, float az) {
    v.xz = rot2(ay) * v.xz;
    v.yz = rot2(ax) * v.yz;
    v.xy = rot2(az) * v.xy;
    float z = v.z + 3.1;
    return v.xy / max(z, 0.5) * 1.15;
}

vec3 demoWireframeCube(vec2 uv, float t) {
    vec2 p = (uv - 0.5) * vec2(uResolution.x / max(uResolution.y, 1.0), 1.0);
    float ax = t * 0.65;
    float ay = t * 0.90;
    float az = t * 0.35;

    float s = 0.58;
    vec2 v0 = project3D(vec3(-s, -s, -s), ax, ay, az);
    vec2 v1 = project3D(vec3( s, -s, -s), ax, ay, az);
    vec2 v2 = project3D(vec3( s,  s, -s), ax, ay, az);
    vec2 v3 = project3D(vec3(-s,  s, -s), ax, ay, az);
    vec2 v4 = project3D(vec3(-s, -s,  s), ax, ay, az);
    vec2 v5 = project3D(vec3( s, -s,  s), ax, ay, az);
    vec2 v6 = project3D(vec3( s,  s,  s), ax, ay, az);
    vec2 v7 = project3D(vec3(-s,  s,  s), ax, ay, az);

    float d = 10.0;
    d = min(d, sdSegment(p, v0, v1));
    d = min(d, sdSegment(p, v1, v2));
    d = min(d, sdSegment(p, v2, v3));
    d = min(d, sdSegment(p, v3, v0));

    d = min(d, sdSegment(p, v4, v5));
    d = min(d, sdSegment(p, v5, v6));
    d = min(d, sdSegment(p, v6, v7));
    d = min(d, sdSegment(p, v7, v4));

    d = min(d, sdSegment(p, v0, v4));
    d = min(d, sdSegment(p, v1, v5));
    d = min(d, sdSegment(p, v2, v6));
    d = min(d, sdSegment(p, v3, v7));

    float edge = smoothstep(0.028, 0.004, d);
    float innerGlow = exp(-d * 18.0) * 0.35;
    float lum = clamp(edge + innerGlow, 0.0, 1.0);
    return vec3(0.92, 0.98, 1.0) * lum;
}

// --- Demo: Twin Fusilli — two volumetric twisted spirals, counter-rotating ---
float fusilliStrand(vec2 d, float t, float spinDir, float seed,
                    float R, float halfLen, float grooveFreq, float tilt, vec2 feather) {
    d = rot2(tilt) * d;
    // Rounded tips along the strand axis
    float lenMask = 1.0 - smoothstep(halfLen - feather.y, halfLen, abs(d.y));
    float body = (1.0 - smoothstep(R - feather.x, R, abs(d.x))) * lenMask;
    if (body <= 0.002) return 0.0;

    // Visible-angle mapping of a cylinder seen from the side
    float theta = asin(clamp(d.x / R, -1.0, 1.0));
    // Helical groove phase: diagonal ridges that screw around the axis over time
    float groovePhase = theta + d.y * grooveFreq + t * spinDir + seed;
    float groove = pow(0.5 + 0.5 * cos(groovePhase), 3.0);
    // Cylinder lambert shading with light from the upper-left
    float lambert = clamp(0.45 + 0.55 * cos(theta - 2.4), 0.0, 1.0);
    // Ridge bulges brighten the silhouette like volumetric pasta ridges
    float bulge = groove * smoothstep(0.55, 1.0, abs(d.x) / R);
    return body * (0.20 + 0.55 * lambert * (0.35 + 0.65 * groove) + 0.85 * bulge);
}

vec3 demoFusilliPair(vec2 uv, float t,
                     float R, float halfLen, float grooveFreq,
                     float tilt, float rot, float offScale, float offBase, vec2 feather) {
    float aspect = uResolution.x / max(uResolution.y, 1.0);
    vec2 p = (uv - 0.5) * vec2(aspect, 1.0);
    vec2 q = rot2(rot) * p;
    float off = offScale * aspect + offBase;
    float sway = 0.02 * sin(t * 0.9);
    float lum =
        fusilliStrand(q - vec2(-off, sway), t,  1.6, 0.0, R, halfLen, grooveFreq, tilt, feather) +
        fusilliStrand(q - vec2( off, -sway), t, -1.6, 1.9, R, halfLen, grooveFreq, tilt, feather);
    lum = clamp(lum, 0.0, 1.0);
    return vec3(0.94, 0.95, 1.0) * lum;
}

// Compact pair laid out along a diagonal, clearly separated
vec3 demoFusilli(vec2 uv, float t) {
    return demoFusilliPair(uv, t, 0.10, 0.30, 13.0, 0.55, 0.40, 0.20, 0.05, vec2(0.030, 0.12));
}

// Giant diagonal spirals filling the screen
vec3 demoFusilliMax(vec2 uv, float t) {
    return demoFusilliPair(uv, t, 0.115, 0.60, 9.0, 0.0, 0.35, 0.31, 0.0, vec2(0.035, 0.14));
}

// --- Demo: Twin Cubes — perspective wireframes with depth-shaded edges ---
vec3 cubeVert(vec3 v, float ax, float ay, float az) {
    v.xz = rot2(ay) * v.xz;
    v.yz = rot2(ax) * v.yz;
    v.xy = rot2(az) * v.xy;
    float z = v.z + 3.1;
    return vec3(v.xy / max(z, 0.5) * 1.15, z);
}

float edgeDepthLum(vec2 p, vec3 a, vec3 b) {
    float d = sdSegment(p, a.xy, b.xy);
    float edge = 1.0 - smoothstep(0.004, 0.020, d);
    // Near edges render bright, far edges dim — per-edge depth cue
    float near = clamp((3.1 - (a.z + b.z) * 0.5) * 1.6, 0.0, 1.0);
    return edge * mix(0.28, 1.0, near) + exp(-d * 16.0) * 0.10;
}

float cubeWire(vec2 q, float s, float ax, float ay, float az) {
    vec3 a = cubeVert(vec3(-s, -s, -s), ax, ay, az);
    vec3 b = cubeVert(vec3( s, -s, -s), ax, ay, az);
    vec3 c = cubeVert(vec3( s,  s, -s), ax, ay, az);
    vec3 d = cubeVert(vec3(-s,  s, -s), ax, ay, az);
    vec3 e = cubeVert(vec3(-s, -s,  s), ax, ay, az);
    vec3 f = cubeVert(vec3( s, -s,  s), ax, ay, az);
    vec3 g = cubeVert(vec3( s,  s,  s), ax, ay, az);
    vec3 h = cubeVert(vec3(-s,  s,  s), ax, ay, az);

    float lum = 0.0;
    lum += edgeDepthLum(q, a, b);
    lum += edgeDepthLum(q, b, c);
    lum += edgeDepthLum(q, c, d);
    lum += edgeDepthLum(q, d, a);
    lum += edgeDepthLum(q, e, f);
    lum += edgeDepthLum(q, f, g);
    lum += edgeDepthLum(q, g, h);
    lum += edgeDepthLum(q, h, e);
    lum += edgeDepthLum(q, a, e);
    lum += edgeDepthLum(q, b, f);
    lum += edgeDepthLum(q, c, g);
    lum += edgeDepthLum(q, d, h);
    return lum;
}

vec3 demoCubesPair(vec2 uv, float t, float s, float sepX, float sepY) {
    float aspect = uResolution.x / max(uResolution.y, 1.0);
    vec2 p = (uv - 0.5) * vec2(aspect, 1.0);
    vec2 cA = vec2(-sepX * aspect,  sepY);
    vec2 cB = vec2( sepX * aspect, -sepY);
    // The pair counter-rotates
    float lum =
        cubeWire(p - cA, s,  t * 0.55,  t * 0.80,  t * 0.30) +
        cubeWire(p - cB, s, -t * 0.50, -t * 0.72, -t * 0.26);
    lum = clamp(lum, 0.0, 1.0);
    return vec3(0.92, 0.98, 1.0) * lum;
}

// Compact pair, clearly separated like the reference
vec3 demoCubes(vec2 uv, float t) {
    return demoCubesPair(uv, t, 0.16, 0.17, 0.21);
}

// Giant pair spanning most of the screen height
vec3 demoCubesMax(vec2 uv, float t) {
    return demoCubesPair(uv, t, 0.27, 0.15, 0.17);
}

// --- Aspect-ratio UV mapping for Photo / Video ---
bool mapMediaUv(vec2 uv, out vec2 mapped) {
    float screenAspect = uResolution.x / max(uResolution.y, 1.0);
    float mediaAspect = uMediaResolution.x / max(uMediaResolution.y, 1.0);

    vec2 centered = uv - 0.5;
    if (uScaleMode == 0) {
        // Fill (CenterCrop)
        if (screenAspect > mediaAspect) {
            centered.y *= mediaAspect / screenAspect;
        } else {
            centered.x *= screenAspect / mediaAspect;
        }
    } else {
        // Fit (Letterbox)
        if (screenAspect > mediaAspect) {
            centered.x *= screenAspect / mediaAspect;
        } else {
            centered.y *= mediaAspect / screenAspect;
        }
    }
    mapped = centered + 0.5;
    return mapped.x >= 0.0 && mapped.x <= 1.0 && mapped.y >= 0.0 && mapped.y <= 1.0;
}

vec3 sampleSource(vec2 uv) {
    if (uSourceMode == 0) {
        if (uDemoPreset == 0) return demoFusilli(uv, uTime);
        if (uDemoPreset == 1) return demoFusilliMax(uv, uTime);
        if (uDemoPreset == 2) return demoCubes(uv, uTime);
        if (uDemoPreset == 3) return demoCubesMax(uv, uTime);
        if (uDemoPreset == 4) return demoWireframeCube(uv, uTime);
        if (uDemoPreset == 5) return demoDnaHelix(uv, uTime);
        if (uDemoPreset == 6) return demoEventHorizon(uv, uTime);
        return demoDnaHelix(uv, uTime);
    }

    vec2 mediaUv;
    if (!mapMediaUv(uv, mediaUv)) {
        return vec3(0.0);
    }

    if (uSourceMode == 1) {
        // Flip Y for Android Bitmap -> GL texture coordinates
        vec2 photoUv = vec2(mediaUv.x, 1.0 - mediaUv.y);
        return texture(uPhotoTex, photoUv).rgb;
    } else {
        vec4 transformed = uVideoMatrix * vec4(mediaUv.x, mediaUv.y, 0.0, 1.0);
        return texture(uVideoTex, transformed.xy).rgb;
    }
}

float computeLuma(vec3 rgb) {
    float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
    luma = (luma - 0.5) * uContrast + 0.5 + uBrightness;
    luma = clamp(luma, 0.0, 1.0);
    if (uInvert > 0.5) {
        luma = 1.0 - luma;
    }
    return luma;
}

void main() {
    // Character cell aspect ratio ~ 0.58 (classic crisp monospace)
    float charAspect = 0.58;
    float cols = max(uColumns, 16.0);
    float rows = max(floor(cols * (uResolution.y / max(uResolution.x, 1.0)) * charAspect), 12.0);
    vec2 gridSize = vec2(cols, rows);

    vec2 cellIndex = floor(vUv * gridSize);
    vec2 cellCenterUv = (cellIndex + 0.5) / gridSize;
    vec2 cellLocalUv = fract(vUv * gridSize);

    // 2x2 sub-cell box filter for clean anti-aliased luminance
    vec2 stepUv = 0.25 / gridSize;
    vec3 c0 = sampleSource(cellCenterUv + vec2(-stepUv.x, -stepUv.y));
    vec3 c1 = sampleSource(cellCenterUv + vec2( stepUv.x, -stepUv.y));
    vec3 c2 = sampleSource(cellCenterUv + vec2(-stepUv.x,  stepUv.y));
    vec3 c3 = sampleSource(cellCenterUv + vec2( stepUv.x,  stepUv.y));
    vec3 srcColor = (c0 + c1 + c2 + c3) * 0.25;

    float luma = computeLuma(srcColor);

    // Map luminance [0..1] to glyph index [0..uCharCount-1]
    float maxIdx = max(uCharCount - 1.0, 1.0);
    float glyphIdx = floor(luma * maxIdx + 0.5);
    float atlasU = (glyphIdx + clamp(cellLocalUv.x, 0.02, 0.98)) / max(uCharCount, 1.0);
    float atlasV = 1.0 - clamp(cellLocalUv.y, 0.02, 0.98);
    float glyphMask = texture(uGlyphAtlas, vec2(atlasU, atlasV)).r;

    // Color mode blending
    vec3 fg;
    if (uColorMode == 1) {
        // Original source colors (boosted slightly for legibility on dark bg)
        fg = clamp((srcColor - 0.5) * uContrast + 0.5 + uBrightness, 0.0, 1.0);
    } else if (uColorMode == 2 || uColorMode == 3) {
        // Duotone / Material You tonal ramp
        fg = mix(uBgColor, uFgColor, clamp(0.25 + luma * 0.85, 0.0, 1.0));
    } else {
        // Monochrome high-contrast terminal look
        fg = uFgColor * clamp(0.35 + luma * 0.75, 0.0, 1.0);
    }

    vec3 color = mix(uBgColor, fg, glyphMask);

    // Optional soft phosphor glow (lightweight 4-neighbor cell bloom)
    if (uGlow > 0.01) {
        vec2 oneCell = 1.0 / gridSize;
        float nLuma = 0.0;
        nLuma += computeLuma(sampleSource(cellCenterUv + vec2( oneCell.x, 0.0)));
        nLuma += computeLuma(sampleSource(cellCenterUv + vec2(-oneCell.x, 0.0)));
        nLuma += computeLuma(sampleSource(cellCenterUv + vec2(0.0,  oneCell.y)));
        nLuma += computeLuma(sampleSource(cellCenterUv + vec2(0.0, -oneCell.y)));
        nLuma *= 0.25;
        vec3 glowTint = (uColorMode == 1) ? srcColor : uFgColor;
        color += glowTint * nLuma * uGlow * 0.45;
    }

    // Optional subtle CRT scanlines
    if (uScanlines > 0.01) {
        float scan = 0.5 + 0.5 * sin(gl_FragCoord.y * 1.75);
        color *= mix(1.0, 0.65 + 0.35 * scan, uScanlines);
    }

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
"""
}
