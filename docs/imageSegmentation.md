
title: Driver's license
version: v1.0.0-draft
status: draft
editor: @hugofr21


# Image Processing: Segmentation and Photometric Normalization

This module describes the processing pipeline responsible for the semantic segmentation and geometric normalization of faces captured for digital identity purposes. The system adopts a modular architecture, where each stage is implemented as a sequential component, allowing scalable maintenance and independent evolution of the computer vision algorithms.

```mermaid
flowchart LR
    classDef input fill:#ECEFF1,stroke:#455A64,stroke-width:2px,color:#263238
    classDef stage fill:#E3F2FD,stroke:#1565C0,stroke-width:2px,color:#0D47A1
    classDef decision fill:#FFF8E1,stroke:#FF8F00,stroke-width:2px,color:#E65100
    classDef success fill:#E8F5E9,stroke:#2E7D32,stroke-width:2px,color:#1B5E20
    classDef failure fill:#FFEBEE,stroke:#C62828,stroke-width:2px,color:#B71C1C
    classDef noteNode fill:#FAFAFA,stroke:#B0BEC5,stroke-width:1px,stroke-dasharray: 4 4,color:#546E7A
    classDef terminal fill:#263238,stroke:#000,color:#fff

    Start(( )):::terminal
Stop(( )):::terminal

subgraph Camera [CAMERA]
direction TB
StartCap["Start Capture"]:::input
Acquire["Acquire Image Frame"]:::input

N_Acquire["<div style='text-align:left; font-size:11px;'>• Image capture from device sensor.<br>• Raw input for biometric pipeline.</div>"]:::noteNode

StartCap --> Acquire
Acquire -.- N_Acquire
end

subgraph FRS [FACE RECOGNITION SYSTEM]
direction LR

subgraph Preprocessing [PREPROCESSING]
direction TB
Detect["Face Detection"]:::stage
Align["Face Alignment / Crop / Resize"]:::stage
Extract["Feature Extraction (Embedding)"]:::stage

N_Detect["<div style='text-align:left; font-size:11px;'>• Detection of facial region using CNN-based<br>detector (e.g., MTCNN, RetinaFace).</div>"]:::noteNode
N_Align["<div style='text-align:left; font-size:11px;'>• Normalization of facial geometry and scale.<br>• Standard input size (112x112 or 160x160).</div>"]:::noteNode
N_Extract["<div style='text-align:left; font-size:11px;'>• Deep neural network inference.<br>• Output: L2-normalized embedding vector.</div>"]:::noteNode

Detect --> Align --> Extract

Detect -.- N_Detect
Align -.- N_Align
Extract -.- N_Extract
end

subgraph Bifurcacao [ ]
direction TB

subgraph Enrollment [ENROLLMENT]
direction TB
Store["Store Biometric Template"]:::stage

N_Store["<div style='text-align:left; font-size:11px;'>• Secure persistence of embedding vector.<br><br>Requirements:<br>- Encrypted storage<br>- Isolated application sandbox<br>- Non-reversible template representation</div>"]:::noteNode

Store -.- N_Store
end

subgraph Verification [VERIFICATION]
direction TB
Score["Compute Similarity Score"]:::stage
Check{"Score ≥ Threshold?"}:::decision
Success["Authentication Success"]:::success
Failure["Authentication Failure"]:::failure

N_Score["<div style='text-align:left; font-size:11px;'>• Distance metrics: Euclidean (L2), Cosine.<br>• Output: similarity score ∈ [0,1].</div>"]:::noteNode

Score --> Check
Check -- "Yes" --> Success
Check -- "No" --> Failure

Score -.- N_Score
end
end
style Bifurcacao fill:none,stroke:none
end

Start --> StartCap
Acquire --> Detect
Extract --> Store
Extract --> Score

Store --> Stop
Success --> Stop
Failure --> Stop
```
## 1. Semantic Segmentation Pipeline

The processing flow integrates the **ML Kit (Google)** library for primary facial detection, coupled with a convolutional neural network (CNN) model specialized in foreground extraction (the *Selfie Segmenter*).

* **Frame Quality Validation:** Before any heavy processing, the captured image undergoes geometric coverage heuristics and matrix calculations. This screening prevents the costly consumption of processing cycles on captures with zero technical viability.
* **Segmentation and Confidence Mask:** The CNN generates a probability matrix (confidence mask) where each pixel is evaluated regarding its probability **$P_{i}$** of belonging to the subject. Foreground preservation is governed by a strict logical constraint, using an empirical threshold of **$P_{i} \geq 0.85$** (85%). Pixels below this threshold are classified as background and purged (converted to strict white: `#FFFFFF`).

## 2. Geometric Expansion and Anatomical Framing

Following segmentation, the system expands the original bounding box to ensure the complete inclusion of the anatomical context (forehead and chin). This recalibration is vital for the quality of subsequent biometric analysis stages.

The calculation of the new boundary coordinates (**$x_{min}, y_{min}, x_{max}, y_{max}$**) is derived from the center coordinates (**$x_{center}, y_{center}$**) and the original dimensions, applying an expansion factor of 0.2 (20%):

$$
x_{min} = \max\left(0, x_{center} - \frac{width \times (1 + expansion)}{2}\right)
$$

$$
y_{min} = \max\left(0, y_{center} - \frac{height \times (1 + expansion)}{2}\right)
$$

$$
x_{max} = \min\left(image\_width, x_{center} + \frac{width \times (1 + expansion)}{2}\right)
$$

$$
y_{max} = \min\left(image\_height, y_{center} + \frac{height \times (1 + expansion)}{2}\right)
$$

## 3. Aspect Ratio Normalization and Framing

For compliance with official documentation standards (35/45 ratio), the segmented image undergoes a forced crop. This operation recalculates the spatial boundaries keeping the fiducial center **$x$** unchanged, while the **$y$** axis is translated to center the holder's head within the regulatory proportion.

## 4. Recentering and Final Composition

As a final step to ensure photometric uniformity, the system performs an absolute recentering onto a pre-allocated white canvas.

1. **Content Identification:** The algorithm scans the RGB matrix to locate the extremities of the non-white segmented content (where **$R < 240 \lor G < 240 \lor B < 240$**).
2. **Displacement Calculation:** The displacement vectors (**$\Delta x, \Delta y$**) required to align the centroid of the extracted content with the geometric center of the target canvas are determined:

$$
\Delta x = \frac{canvas\_width}{2} - \frac{x_{min} + x_{max}}{2}
$$

$$
\Delta y = \frac{canvas\_height}{2} - \frac{y_{min} + y_{max}}{2}
$$

The application of these displacements results in a standardized final image, with a purely white background and the holder's face perfectly centered. This approach guarantees robustness against lighting variations and complex backgrounds, maximizing the accuracy of the biometric data required for the issuance of secure verifiable credentials.