---
title: Anti-Spoofing
version: v1.0.0-draft
status: draft
editor: @hugofr21
---
# Anti-Spoofing

The `AnalyzerGuilinecCardService` acts as the primary defense line against presentation attacks (*spoofing*) during identity document capture. Rather than relying exclusively on artificial intelligence models, the system employs a deterministic approach based on computational geometry, signal processing (Frequency Analysis), and sensor fusion (accelerometer and gyroscope) to detect spoofing patterns. This architecture was designed to be resource-efficient, ensuring fast and accurate responses even on mobile devices with processing constraints.

The final result is a weighted score, calculated from multiple attack vectors, which classifies the sample into one of three categories: authentic document, screen attack (digital reproduction), or printed copy.

The spoofing detection processes are orchestrated by a Finite State Machine (FSM), responsible for managing the sequence of mathematical calculations:

## Dichromatic Reflection Analysis

Light incident on an object is divided into diffuse reflection and specular reflection. Analyzing the proportion between these two types of reflection allows distinguishing a genuine physical object from a flattened image (printed or digital), since real objects exhibit distinct specular reflection ratios compared to two-dimensional surfaces.

The captured image is converted to the YCbCr color space, where the Y channel represents luminance and the Cb and Cr channels represent chrominance. The analysis is performed independently for each channel, isolating reflection patterns characteristic of the object's materiality. The algorithm calculates the ratio of supersaturated pixels (indicative of specular reflection) relative to the total pixels in the sample, using an empirically calibrated threshold to classify the capture as authentic or *spoofing*.

## Moiré Pattern Detection

Mitigating replay attacks on digital screens is achieved through Moiré pattern detection — visual interference generated when a pixel mesh from a screen overlaps with the image sensor's capture matrix. The algorithm employs image processing techniques to isolate these anomalies, which are strong indicators of a *spoofing* attempt via a digital device.

The calculation involves applying a high-frequency Laplacian filter to enhance edges and image microdetails. Subsequently, a Fast Fourier Transform (FFT) is performed to identify the presence of structured periodic patterns. Detection of anomalous magnitude peaks in the frequency domain signals high probability of an attack executed through a screen.

## OVI Consistency via Gyroscope

To combat the use of high-resolution photocopies and static photographs lacking natural movement, the system calculates the camera's angular difference between successive *frames*, extracting data from the device's gyroscope (with emphasis on the *Yaw* axis). If the angular variation is below a minimum threshold established during the capture window, the system flags the sample as suspected static presentation.

In parallel, during document data extraction, the system calculates the 3D Euclidean distance of the mean RGB value between *frames*. This calculation aims to detect the presence of Optically Variable Inks (OVI) and holograms, which exhibit dynamic color and light intensity variations under different perspective angles. Null or insignificant chromatic variation suggests the absence of genuine optical security elements, typifying a printed copy attack.

## Injection Attack Prevention

Beyond visual inspection techniques, the system incorporates preventive controls against camera *bypasses*. To mitigate static *frame* injection, a duplication detection mechanism is implemented: each captured *frame* is compared with immediate history through a perceptual *hashing* function.

Using Hamming distance calculation between *frame* *hashes*, the system evaluates the video stream. If an identical sequence is detected beyond an acceptable consecutive tolerance limit, the system infers an injection attempt, blocking the capture or requiring the holder to move the device to prove session authenticity.

Aggregating these mathematical verification steps enables the system to certify not only the semantic validity of the presented document but also its three-dimensional physical integrity. By correlating frequency-domain behaviors with geometric and photometric integrity in real-time, the engine achieves the institutional confidence level required for secure issuable verifiable credentials.

---

### Anti-Spoofing Techniques for Facial Liveness Detection

The architecture supports extensibility for biometric validation through the following complementary techniques:

- **Dichromatic Reflection Analysis:** Detection of flat surfaces (paper masks or screens)
- **Normal Vector Field Estimation:** Microtexture analysis to validate volumetry
- **Moiré Pattern Detection:** High-density digital screen filtering
- **Frequency Analysis (FFT):** Identification of synthetic non-natural periodic patterns
- **OVI Verification Combined with Accelerometer:** Hologram validation synchronized with movement
- **Planar Homography Consistency:** Object rigidity detection (distinction between flexible real features and rigid reproductions)
- **3D Depth Analysis:** Topographic mapping with ARCore assistance

```mermaid
flowchart LR
    classDef input fill:#E3F2FD,stroke:#1565C0,stroke-width:2px,color:#000
    classDef stage fill:#E8F5E9,stroke:#2E7D32,stroke-width:2px,color:#000
    classDef decision fill:#FFF8E1,stroke:#FF8F00,stroke-width:2px,color:#000
    classDef parallel fill:#F3E5F5,stroke:#8E24AA,stroke-width:2px,color:#000

    classDef success fill:#C8E6C9,stroke:#2E7D32,stroke-width:2px,color:#000
    classDef failure fill:#FFCDD2,stroke:#C62828,stroke-width:2px,color:#000
    classDef warning fill:#FFE0B2,stroke:#E65100,stroke-width:2px,color:#000

    classDef noteNode fill:#FAFAFA,stroke:#B0BEC5,stroke-width:1px,stroke-dasharray: 4 4,color:#455A64

    Camera["Input<br>Camera Frame"]:::input


    OCR["Parallel Process<br>OCR Pipeline"]:::parallel
    N_OCR["<div style='text-align:left; font-size:11px;'>• OCR execution is independent<br>from anti-spoofing decision</div>"]:::noteNode


    subgraph Pipeline [Anti-Spoofing Pipeline]
        direction LR
        L1["Stage 1<br>Image Quality Gate<br>(Laplacian Var / Brightness / Contrast)"]:::stage
        L2["Stage 2<br>Sensor Warm-up Filter<br>(discard first N frames)"]:::stage
        L3["Stage 3<br>Temporal Aggregation<br>(sliding window W frames)"]:::stage
        L4["Stage 4<br>Active Liveness Check<br>(user feedback if marginal)"]:::stage

        N_L3["<div style='text-align:left; font-size:11px;'>• Decision requires persistence<br>(e.g., >= K consecutive fails)</div>"]:::noteNode

        L1 -- "pass" --> L2
        L2 --> L3
        L3 --> L4

        L3 -.- N_L3
    end

    Decision{{"Decision Engine<br>(anti-spoofing verdict)"}}:::decision


    Poor["Output<br>PoorImageQuality"]:::failure
    Success["Output<br>Success"]:::success
    Guidance["Output<br>GuidanceNeeded"]:::warning
    Fail["Output<br>SecurityCheckFail"]:::failure
    
    Camera --> OCR
    OCR -.- N_OCR

    Camera --> L1
    L1 -- "reject" --> Poor

    L4 --> Decision

    Decision -- "live" --> Success
    Decision -- "marginal" --> Guidance
    Decision -- "spoof" --> Fail
```

---


## References

1. [FFT Magic: Unlocking the Secrets of Signals with Python and the Intuition Behind It](https://medium.com/data-science-collective/fft-magic-unlocking-the-secrets-of-signals-with-python-and-the-intuition-behind-b43915185b5d)
2. [Similarity Hashing and Perceptual Hashes](https://billatnapier.medium.com/similarity-hashing-and-perceptial-hashes-963fba36c8b5)
