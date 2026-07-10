# Security Architecture & Threat Model

## Structural Elements of the Threat Model (EUDI Wallet)

1. **Trust Boundaries ( *Trust Boundaries* ):**
  * *Secure Hardware (eSE / StrongBox):* Maximum trust level. Physical isolation where private keys and critical cryptographic operations reside. Protected against extraction even on rooted devices.
  * *Mobile Operating System (Hostile/Untrusted):* Application runtime environment. Considered hostile due to the possibility of malware, transient memory interception, or privilege escalation.
  * *Network / External Ecosystem:* Public boundary where flows transit via proximity channels (BLE/NFC) or remote channels (Internet) toward third parties (Verifiers and Issuers).
2. **Actors & Assets ( *Actors & Assets* ):**
  * *Actors:* Holder, Issuer (Issuer/QTSP), and Verifier (Relying Party).
  * *Data Assets:* Personally Identifiable Data (PID), Electronic Attribute Assertions (QEAAs), Device Private Keys, Session Metadata, and Audit Logs.
3. **Processes ( *Processes* ):**
  * *Wallet Application (WDA):* Orchestrates business logic, generates user interfaces, and interacts with local storage.
  * *Cryptographic / Proof Generation Module:* Process focused on deriving keys, signing presentation requests (mDoc/SD-JWT), and executing Zero-Knowledge Proofs (ZKP).
  * *Local User Authentication Module (LUA):* Validates user biometrics or PIN before authorizing access to data.
4. **Storage ( *Storage* ):**
  * *Encrypted Local Database (SQLCipher):* Stores structured PID and QEAA attributes, protected by AEAD symmetric cipher derived from hardware-guarded keys.
  * *Hardware Keystore:* Physical storage of non-exportable asymmetric keys.
5. **Data Flows ( *Data Flows* ):**
  * *Issuance (OIDC4VCI):* Flow of receiving credentials from Issuer to Wallet.
  * *Presentation (OpenID4VP / mDoc):* Flow of sharing selective attributes from Wallet to Verifier.

### Threat Modeling
  * [View details STRIDE](./STRIDE.MD)

  * [View details LINDDUN](./LINDDUN.md)

  * [View details PASTA](./PASTA.md)

## 1. System Context and Trust Boundaries
This document outlines the formal threat model for the Mobile Identity Wallet, evaluated under a strict **Zero-Trust architecture**. The application operates in potentially hostile mobile environments, requiring robust hardware-backed cryptographic guarantees.

````mermaid
graph TD

    %% Class Definitions
    classDef goal fill:#d32f2f,stroke:#b71c1c,stroke-width:2px,color:#fff,font-weight:bold;
    classDef vector fill:#e65100,stroke:#e65100,stroke-width:2px,color:#fff;
    classDef attack fill:#ffb74d,stroke:#ef6c00,stroke-width:1px,color:#000;
    classDef mitigation fill:#2e7d32,stroke:#1b5e20,stroke-width:2px,color:#fff,stroke-dasharray: 5 5;
    classDef note fill:#1976d2,stroke:#0d47a1,stroke-width:1px,color:#fff;

    %% Root Node / Goal
    G0((Identity Theft & <br> Financial Fraud)):::goal

    %% Level 1 Attack Vectors
    A1[Unauthorized Credential <br> Presentation]:::vector
    A2[mDoc/mDL Exfiltration <br> & Decryption]:::vector
    A3[Fraudulent Issuance <br> via Issuer]:::vector

    G0 --- A1
    G0 --- A2
    G0 --- A3

    %% Branch 1: Unauthorized Presentation
    A1 --- A1_1[ECDSA Signature Forgery]:::attack
    A1 --- A1_2[BLE/NFC Presentation Replay]:::attack
    
    A1_1 --- A1_1_1[Biometric Spoofing <br> FIDO2 / WebAuthn]:::attack
    A1_1 --- A1_1_2[Physical Compromise of <br> TEE/Secure Enclave]:::attack

    %% Branch 2: Exfiltration
    A2 --- A2_1[SQLCipher Master Key <br> Extraction]:::attack
    A2 --- A2_2[Malicious Supply Chain <br> Exploitation]:::attack
    
    A2_1 --- A2_1_1[Runtime RAM <br> Memory Dump]:::attack
    A2_1_1 --- A2_1_1_1[OS Compromise <br> Root / Jailbreak]:::attack

    %% Branch 3: Fraudulent Issuance
    A3 --- A3_1[MitM Attack on <br> OIDC4VCI Flow]:::attack

    %% Mitigations
    M1([Liveness Detection <br> 3D Biometric PAD]):::mitigation
    M2([Hardware Certification <br> CC EAL 4+]):::mitigation
    M3([Channel Encryption <br> ISO 18013-5 & Nonces]):::mitigation
    M4([Keystore Binding & <br> StrongBox Enforcement]):::mitigation
    M5([RASP Detection & <br> Play Integrity API]):::mitigation
    M6([DevSecOps Pipeline <br> SAST & SBOM Audit]):::mitigation
    M7([TLS Pinning & <br> DPoP Validation]):::mitigation

    %% Architectural Note
    N1([EC Diffie-Hellman for <br> Session Key Agreement]):::note

    %% Mitigation Edges
    A1_1_1 -. Mitigated by .-> M1
    A1_1_2 -. Mitigated by .-> M2
    A1_2 -. Mitigated by .-> M3
    A2_1 -. Mitigated by .-> M4
    A2_1_1_1 -. Mitigated by .-> M5
    A2_2 -. Mitigated by .-> M6
    A3_1 -. Mitigated by .-> M7
    
    %% Edge from Note to Mitigation
    N1 -. Cryptographic Basis .-> M3
````
### Core Architectural Facts
* **Cryptography:** All key generation occurs within a Hardware-backed Trusted Execution Environment (TEE/StrongBox). Signatures rely on ECDSA (P-256/Brainpool).
* **Storage:** Verifiable Credentials (mDoc/mDL) are encrypted at rest using SQLCipher (AES-256-GCM).
* **Network & Provisioning:** Interactions with Issuers utilize the OIDC4VCI specification, fortified with PKCE and Proof of Possession (DPoP) tokens.
* **Authentication:** User Verification leverages FIDO2/WebAuthn standards bound to the device's Secure Element.

## 2. Threat Landscape (STRIDE Matrix)
The attack tree is modeled against ultimate system compromise goals: **Unauthorized Credential Presentation** and **Systemic Identity Theft**. The following critical attack vectors have been identified and mapped to the STRIDE taxonomy:

### A. Privilege Escalation & Hardware Attacks
* **OS Compromise (`Elevation of Privilege`):** Attackers gaining root/jailbreak access to bypass standard OS sandboxing.
* **TEE Compromise (`Elevation of Privilege`):** Physical exploitation or side-channel attacks against the Secure Enclave following boot chain tampering.

### B. Information Disclosure & Tampering
* **Credential Exfiltration (`Information Disclosure`):** Extraction of the SQLite database and its subsequent decryption via in-memory key dumping.
* **Supply Chain Exploitation (`Tampering`):** Execution of malicious payloads introduced via third-party dependencies.

### C. Spoofing & Network Attacks
* **Biometric Spoofing (`Spoofing`):** Defeating the User Verification mechanism via presentation attacks (e.g., masks, deepfakes).
* **Presentation Replay (`Spoofing/Tampering`):** Interception of BLE/NFC engagement protocols during credential presentation to a Relying Party.
* **Authorization Code Interception (`Information Disclosure`):** Man-in-the-Middle (MitM) attacks during the OIDC credential issuance flow.

## 3. Mitigation Strategies & Security Controls
To neutralize the attack paths, the architecture dictates the implementation of the following verifiable controls:

| Mitigation Control | Description | Targeted Threat |
| :--- | :--- | :--- |
| **Hardware Attestation & CC EAL4+** | Cryptographic proof of device integrity and use of certified Secure Enclaves. | OS & TEE Compromise |
| **StrongBox Enforcement** | Flagging `setIsStrongBoxBacked(true)` to ensure keys never reside in standard RAM. | ECDSA Signature Forgery |
| **Keystore Binding** | Tying the SQLCipher master key directly to the Android Keystore, requiring biometric unlock. | SQLCipher Key Extraction |
| **Liveness Detection (PAD)** | Multi-modal 3D biometric checks to ensure user presence. | Biometric Spoofing |
| **ISO 18013-5 Session Binding** | Implementation of ephemeral Nonces and Device Engagement channel encryption. | Presentation Replay |
| **TLS Pinning & DPoP** | Enforcing Certificate Transparency and binding OIDC tokens to the device's public key. | Auth Code Interception |
| **DevSecOps Pipeline** | Continuous enforcement of CodeQL (SAST), OWASP ZAP (DAST), and strict SBOM auditing. | Malicious Dependencies |

## 4. DC API & ZKPs
*(Section reserved for Decentralized Credential API and Zero-Knowledge Proofs specifications).*

## 5. Brute-Force and Cryptographic Attack Mitigations
* **High-entropy salts & iteration count** for PBKDF2 make offline dictionary attacks computationally infeasible.
* **Rate limiting** on PIN entry (exponential back-off, temporary lock-out).
* **Hardware rate-limiting:** StrongBox and the TEE enforce a strict limit on the number of key usage attempts per second, preventing rapid trial of derived keys.
* **Constant-time comparisons** for authentication tokens to avoid side-channel timing attacks.
* **Secure random number generation** (via `SecureRandom` / `KeyGenParameterSpec`) for all Initialization Vectors (IVs), salts, and nonces.

---

## 6. Security Flow Overview

A comprehensive, end-to-end overview of how security is integrated into the software development lifecycle (SDLC). Each stage establishes a mandatory **security gate** that must be passed before the next phase can initiate, supported by a set of recommended industry-proven tools.

### 6.1. Structured Analysis & Design Technique (SADT)
* **Main Activities:**
    * Model functional and non-functional security requirements.
    * Produce data-flow diagrams (DFD), sequence diagrams, and component diagrams.
    * Define security controls (confidentiality, integrity, availability) and governance policies.
    * Build a Threat Model (e.g., STRIDE, PASTA).
* **Recommended Tools:**
    * **OpenSSF Scorecard:** Checks for secure repository practices (branch protection, signed commits, etc.).
    * **Threat-Modeling:** Microsoft SDL, OWASP ASVS, OWASP Threat Dragon, PlantUML.

### 6.2. Software Composition & Architecture Scan (SCAS)
* **Main Activities:**
    * Create an inventory of every third-party component (Software Bill of Materials - SBOM).
    * Analyze licenses and compliance (MIT, GPL, Apache, etc.).
    * Scan public vulnerability databases (CVE) for utilized components and base images.
    * Evaluate architectural risk (critical components, single points of failure).
* **Recommended Tools:**
    * **OWASP Dependency-Check** (Gradle/Maven plugin).
    * **GitHub Dependabot:** Automated pull-requests for updates.
    * **Snyk / WhiteSource / OSS Index:** Complementary SCA services.
    * **OpenSSF Scorecard:** Additional checks such as `security-updates` and `maintained`.

### 6.3. Static Application Security Testing (SAST)
* **Main Activities:**
    * Perform static analysis of source code to locate bugs, anti-patterns, and logical vulnerabilities.
    * Enforce secure-coding rules (e.g., OWASP ASVS Level 2).
    * Generate **Quality Gates** that block merges when thresholds are not met.
* **Recommended Tools:**
    * **SonarQube:** Kotlin-specific rules (Detekt, ktlint, code smells).
    * **CodeQL:** Custom Kotlin queries (SQL injection, path traversal, insecure deserialization).
    * **Checkmarx / Fortify / SpotBugs:** Optional or enterprise alternatives.

### 6.4. Dynamic Application Security Testing (DAST)
* **Main Activities:**
    * Scan the **running** application (API, UI, WebSocket).
    * Run automated penetration tests: code injection, XSS, CSRF, broken authentication, broken access control.
    * Perform brute-force and fuzzing of parameters and file uploads.
* **Recommended Tools:**
    * **OWASP ZAP:** Baseline scan and active scan.
    * **Burp Suite Community:** Intruder and scanner modules.
    * **Gauntlt:** Security tests expressed as BDD scenarios.
    * **Nikto / Wapiti / Arachni:** Additional vulnerability scanners.

### 6.5. Secure Application Lifecycle & Stewardship (SALS)
* **Main Activities:**
    * Continuously monitor security-related metrics (latency, error rates, anomalies).
    * Collect distributed telemetry and trace critical transactions.
    * Centralize **immutable** logs, correlate events, and emit incident alerts.
    * Automate vulnerable-dependency upgrades and rotate secrets/keys.
    * Operate a formal Incident Response Plan (IRP) and maintain forensic audit trails.
* **Recommended Tools:**
    * **OpenTelemetry:** Instrumentation and Collector.
    * **Prometheus & Alertmanager:** Metric collection and alerting.
    * **Grafana:** Real-time visibility dashboards.
    * **Elastic SIEM:** Write-once logs, correlation, and security dashboards.
    * **Loki / Fluentd:** Log aggregation.
    * **HashiCorp Vault:** Automatic token/secret rotation.
    * **Argo Rollouts / Flux:** Safe, automated image upgrades.
---

## 7. Encryption of Sensitive Data

| Component | Algorithm | Mode | Rationale |
| :--- | :--- | :--- | :--- |
| **Database / file-level storage** | **AES-256** | **GCM (Galois/Counter Mode)** | Provides confidentiality **and** authenticity (integrity) in a single primitive. GCM’s built-in authentication tag protects against ciphertext tampering. |
| **Derived secret keys** | **PBKDF2-HMAC-SHA-256** | – | A password-based key-derivation function that turns the user’s PIN (or pass-phrase) into a strong 256-bit key. A per-wallet random salt and a high iteration count (≥ 200,000) make offline brute-force attacks computationally expensive. |
| **Transient session keys** | **AES-256-GCM** | – | Used for encrypting data that is only needed for the lifetime of a presentation or a remote QES flow. Session keys are generated on-the-fly and destroyed after use. |

> **Note:** All encrypted payloads include the GCM authentication tag (16 bytes) and the IV (12 bytes) stored alongside the ciphertext. The IV is generated with a cryptographically secure random number generator.

---

## 8. Hardware-Backed Key Management
* **StrongBox (Android Keystore):** When the device supports StrongBox, the wallet stores the **master encryption key** inside the hardware-isolated Trusted Execution Environment (TEE). The key never leaves the TEE in clear text and can only be accessed after successful user authentication.
* **Software fallback:** On devices lacking StrongBox, keys are stored in the regular Android Keystore, which is still hardware-backed on most modern phones (Secure Element / TEE).
* **Biometric-protected key access:** The wallet registers a **BiometricPrompt** that gates the retrieval of the decryption key from the Keystore. The biometric check is combined with the user-chosen PIN (see next section) to provide **two-factor authentication (2FA)**.

---

## 9. Multi-Factor Authentication (MFA)

| Factor | Implementation | Security Contribution |
| :--- | :--- | :--- |
| **PIN** | User-chosen numeric PIN (minimum 6 digits) is processed by PBKDF2 to derive an **encryption key**. | Provides something the user **knows**. PBKDF2 makes offline attacks costly. |
| **Biometrics** | Fingerprint / Face-recognition via Android’s **BiometricPrompt**. The biometric credential is never exposed to the app; only a cryptographic token from the TEE is returned. | Provides something the user **is**. The token can be combined with the PIN-derived key, ensuring that both are required to unlock the wallet. |
| **Device attestation** (optional) | SafetyNet / Play Integrity API checks that the device is genuine and unmodified. | Raises the bar against rooted or tampered devices attempting to extract secrets. |

If any factor fails, the wallet enforces **exponential back-off** and **temporary lock-out** (e.g., 30s → 1 min → 5 min → permanent wipe after configurable attempts).

---

## 10. Anti-Spoofing & Identity-Falsification Mitigation
* **Liveness detection:** The biometric APIs perform built-in liveness checks (e.g., challenge-response for face or anti-finger-lift for fingerprints).
* **Secure element attestation:** The StrongBox/TEE provides attestation certificates that prove the key material resides in a trusted environment, making it harder for malware to spoof a legitimate credential.
* **Presentation binding:** Verifiable Credentials are signed by trusted issuers (X.509 or JSON-Web-Signature) and are **cryptographically bound** to the holder’s private key stored in the Keystore. Any tampering results in a verification failure on the verifier side.
* **Replay protection:** Each presentation includes a **nonce** (or timestamp) issued by the verifier, ensuring that captured presentations cannot be replayed.

---

## 11. Credential Issuance & Self-Issued Credentials
* **Issuer-signed credentials:** The wallet can receive credentials from external trusted issuers via the OIDC-VC flow. All received VCs are validated (signature chain, revocation status, expiration) before being stored.
* **Self-issued credentials:** The wallet supports the **Self-Issued OpenID Provider** pattern, enabling users to create **personal identifiers** (e.g., a "wallet address" or "self-asserted PID"). These credentials are **self-signed** with the wallet’s private key (hardware-protected) and can be presented to services that accept self-issued VCs, subject to appropriate policy checks on the verifier side.

All issued credentials are stored encrypted (AES-GCM) and are only accessible after successful MFA.