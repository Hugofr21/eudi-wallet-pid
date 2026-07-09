title: Liveness
version: v1.0.0-draft
status: draft
editor: @hugofr21


# Liveness

The real-time image capture process enables analysis of the wallet user's portrait and subsequently verify whether the obtained images correspond to the user and can uniquely identify them.
In the context of verifiable credential issuance, it is imperative to link them to the identity of the holder.

However, to implement anti-spoofing protection it is necessary to detect whether it is a real person or an image or video. To achieve this, the security of the process is obtained through random orchestration of challenges managed by a finite state machine. The system does not present passive movements but active proofs of movements: smiling, nodding the head up and down, looking to the right, looking to the left, and opening and closing the eyes.

	org.iso.18013.5.1	portrait
### Facial Detection and Landmark Extraction

The first step consists of locating the face in the video stream and extracting fiducial facial points. Using deep learning algorithms, points are identified in x, y, z coordinates that delineate the facial structure, eyes, eyebrows, nose, mouth, and jaw contour. This geometric mesh serves as the basis for all subsequent biometric calculations.

### Head Pose Estimation

To verify the three-dimensionality of the object and the user's ability to follow instructions, the system estimates head pose in real time. Normally, a photograph is projected onto the 2D plane of a 3D facial model and rotation angles are calculated on the Pitch (up/down), Yaw (left/right), and Roll (lateral tilt) axes. This allows validation of whether the user is looking at the camera and responding to movement challenges.

### Blink Detection and Eye Aspect Ratio Calculation
As proof of involuntary liveness, the system monitors eye blinking through the Eye Aspect Ratio metric, consisting of analyzing six points in the eyes to calculate the vertical opening of the eyelids relative to the horizontal width of the eye. The formula applied is
**$EAR = \frac{||p_2 - p_6|| + ||p_3 - p_5||}{2 ||p_1 - p_4||}$**

An open eye has a high and relatively constant EAR. If a blink occurs during capture, the EAR value drops abruptly to a value close to zero.

### Smile Detection and Mouth Opening (Mouth Aspect Ratio)
Mouth movements are monitored through algorithms that detect smiling and Mouth Aspect Ratio, which calculates the ratio between the vertical opening of the lips and the horizontal distance between the corners of the mouth. These metrics are used to validate action challenges with mouth opening or smiling.

**$MAR = \frac{d_v}{d_h}$**

A smile with closed lips increases the horizontal distance and compresses the vertical, resulting in a MAR value that decreases or remains low. Often, this is combined with the smile probability calculated by ML Kit classifiers that analyze facial muscle tension. When opening the mouth, the value must rise above a certain threshold to prove that the jaw has moved vertically, something very difficult to simulate with a static photo.

Immediately after the state machine confirms the success of liveness detection, the system captures in a controlled 3D environment and is subjected to a biometric cross-verification.


### Steps
1- Facial Detection and Landmark Extraction
2- Head Pose Estimation
3- Blink Detection and Eye Aspect Ratio Calculation
4- Smile Detection and Mouth Opening (Mouth Aspect Ratio)
5- Challenge Orchestration - State Machine
6- Capture and Cross-Verification of Authenticity


````mermaid
flowchart TB

    classDef acquisition fill:#E3F2FD,stroke:#1565C0,stroke-width:2px
    classDef liveness fill:#FFF3E0,stroke:#EF6C00,stroke-width:2px
    classDef biometric fill:#E8F5E9,stroke:#2E7D32,stroke-width:2px
    classDef decision fill:#FCE4EC,stroke:#AD1457,stroke-width:2px

    Start((Start))

    subgraph A["Image Acquisition"]
        direction LR
        Camera[Video Capture]
        Face[Face Detection]
        Landmarks[Facial Landmark Extraction]
        Camera --> Face --> Landmarks
    end

subgraph B["Active Liveness Detection"]
direction LR
Pool[Challenge Repository]
Select[Random Challenge Selection<br/>(3 of 5)]
FSM[Finite State Machine]
Validate[Challenge Validation]
Next{"More Challenges?"}

Pool --> Select --> FSM --> Validate --> Next
Next -- Yes --> FSM
end

subgraph C["Biometric Verification"]
direction LR
Capture[Capture Reference Frame]
Align[Face Alignment]
Embedding[Embedding Extraction]
Similarity[Similarity Computation]

Capture --> Align --> Embedding --> Similarity
end

subgraph D["Authentication"]
direction LR
Decision{"Similarity ≥ Threshold?"}
Success[Authentication Successful]
Failure[Authentication Failed]

Decision -- Yes --> Success
Decision -- No --> Failure
end

Start --> Camera
Landmarks --> Pool
Next -- No --> Capture
Validate -- Failed --> Failure
Similarity --> Decision

Camera:::acquisition
Face:::acquisition
Landmarks:::acquisition

Pool:::liveness
Select:::liveness
FSM:::liveness
Validate:::liveness
Next:::liveness

Capture:::biometric
Align:::biometric
Embedding:::biometric
Similarity:::biometric

Decision:::decision
Success:::decision
Failure:::decision
````
## Anti-spoofing: Feature Learning Approach for Deep Face Recognition

The process of continuous authenticity verification of the digital wallet is based on a one-to-one facial biometric verification system, executed entirely in an embedded computing environment to safeguard the privacy and integrity of cryptographic data. During the initial onboarding integration phase, the system captures a photometric image that, after processing, acts as the anchor for the holder's identity. In subsequent operational interactions that require unequivocal proof of ownership, the architecture triggers a new reading cycle, performing validation against the first established primary instance. To enable this operation under rigorous memory and latency constraints on mobile devices, the infrastructure employs the coupling of ML Kit and TensorFlow Lite libraries.

The processing algorithm begins with the submission of the raw bitmap to a frame analyzer supervised by ML Kit. This first layer is responsible for performing facial detection and extracting landmarks in real time, whose spatial coordinates define the points of each facial feature. This affine alignment is a non-negotiable prerequisite before the quadratic crop of the tensor, as it ensures that the topographic proportions injected into the neural network are strictly consistent with the spatial pattern imposed in its original training base.

With the pixel matrix properly analyzed and color channels normalized to the network evaluation scale, the data flow transitions to the embedded system managed by TensorFlow Lite. The computational core, based on the MobileNetV3 architecture, executes an optimized forward pass. The implementation of depth-wise separable convolutions in this topology acts as a critical mathematical compression mechanism, reducing computational cost by an exponential factor to enable operation under the device's battery load. As a result of the embedded system, a high-dimensional feature vector encapsulates the geometric biometry projected onto the normalized scale of the captured image vector. Next, the same data processing is performed on the saved bitmap from the onboarding capture in the wallet configuration, and as a result, two normalized vectors are obtained. The degree of similarity is applied by calculating the dot product between the two numerical abstractions. If the coefficient of mathematical proximity exceeds the empirically validated security constant, the controller sends a response with the score and message type projected to inform the user whether it is the same person, in addition to constructing a randomly orchestrated movement challenge with a series of steps to perform.

**References**

[1]: https://medium.com/data-science/implementing-liveness-detection-with-google-ml-kit-5e8c9f6dba45
