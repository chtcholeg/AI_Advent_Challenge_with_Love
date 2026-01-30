# Android Build Environment
# Based on Ubuntu 24.04 with Android SDK, Java, and Gradle
FROM ubuntu:24.04

LABEL maintainer="Android Builder"
LABEL description="Docker image for building Android applications"

# Prevent interactive prompts during package installation
ENV DEBIAN_FRONTEND=noninteractive

# Install dependencies (including emulator requirements)
RUN apt-get update && apt-get install -y \
    curl \
    wget \
    unzip \
    git \
    openjdk-17-jdk \
    build-essential \
    # Emulator dependencies
    libpulse0 \
    libnss3 \
    libxcomposite1 \
    libxcursor1 \
    libxi6 \
    libxtst6 \
    libasound2 \
    libgl1-mesa-glx \
    libglu1-mesa \
    libx11-xcb1 \
    libxcb-glx0 \
    libxkbcommon-x11-0 \
    libxrender1 \
    libxrandr2 \
    libxfixes3 \
    libxdamage1 \
    # KVM for hardware acceleration
    qemu-kvm \
    libvirt-daemon-system \
    && rm -rf /var/lib/apt/lists/*

# Set Java environment
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH=$PATH:$JAVA_HOME/bin

# Create android user with KVM access
RUN useradd -m -s /bin/bash android && \
    mkdir -p /home/android/sdk && \
    mkdir -p /home/android/.android/avd && \
    chown -R android:android /home/android && \
    # Add to kvm group for hardware acceleration
    usermod -aG kvm android || true

USER android
WORKDIR /home/android

# Install Android SDK Command Line Tools
ENV ANDROID_HOME=/home/android/sdk
ENV ANDROID_SDK_ROOT=$ANDROID_HOME
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
ENV PATH=$PATH:$ANDROID_HOME/platform-tools
ENV PATH=$PATH:$ANDROID_HOME/build-tools/34.0.0

# Download and install Command Line Tools
RUN mkdir -p $ANDROID_HOME/cmdline-tools && \
    cd $ANDROID_HOME/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip && \
    unzip -q commandlinetools-linux-*_latest.zip && \
    rm commandlinetools-linux-*_latest.zip && \
    mkdir -p latest && \
    mv cmdline-tools/* latest/ 2>/dev/null || true

# Accept licenses and install SDK components (including emulator)
RUN yes | sdkmanager --licenses && \
    sdkmanager "platform-tools" \
               "platforms;android-34" \
               "build-tools;34.0.0" \
               "extras;google;m2repository" \
               "extras;android;m2repository" \
               "emulator" \
               "system-images;android-34;google_apis;x86_64"

# Add emulator to PATH
ENV PATH=$PATH:$ANDROID_HOME/emulator

# Create AVD (will be persisted in volume)
RUN echo "no" | avdmanager create avd \
    --name pixel6_api34 \
    --package "system-images;android-34;google_apis;x86_64" \
    --device "pixel_6" \
    --force || true

# Install Gradle
ENV GRADLE_VERSION=8.5
RUN cd /home/android && \
    wget -q https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip && \
    unzip -q gradle-${GRADLE_VERSION}-bin.zip && \
    rm gradle-${GRADLE_VERSION}-bin.zip && \
    ln -s /home/android/gradle-${GRADLE_VERSION}/bin/gradle /usr/local/bin/gradle || true

ENV PATH=$PATH:/home/android/gradle-${GRADLE_VERSION}/bin

# Create workspace directory
RUN mkdir -p /home/android/workspace

# Set working directory for builds
WORKDIR /home/android/workspace

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD java -version && gradle --version || exit 1

# Default command
CMD ["/bin/bash"]
