#!/bin/bash

###############################################################################
# Onmeet Backend - GCP e2-standard-2 Infrastructure Setup Script
# Ubuntu 22.04 LTS
# Target: 8GB RAM with 8GB Swap, Docker & Docker Compose installation
###############################################################################

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Onmeet Backend Infrastructure Setup${NC}"
echo -e "${GREEN}========================================${NC}"

# Function to print status
print_status() {
    echo -e "${GREEN}[✓]${NC} $1"
}

print_error() {
    echo -e "${RED}[✗]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[!]${NC} $1"
}

# Check if running as root
if [ "$EUID" -eq 0 ]; then
    print_error "Please do not run this script as root. Use a regular user with sudo privileges."
    exit 1
fi

# Check if sudo is available
if ! command -v sudo &> /dev/null; then
    print_error "sudo is required but not installed. Please install sudo first."
    exit 1
fi

###############################################################################
# 1. System Update
###############################################################################
echo -e "\n${YELLOW}Step 1: Updating system packages...${NC}"
sudo apt-get update -y
sudo apt-get upgrade -y
print_status "System packages updated"

###############################################################################
# 2. Create Swap File (Auto-detect available space)
###############################################################################
echo -e "\n${YELLOW}Step 2: Creating Swap memory...${NC}"

# Check available disk space
AVAILABLE_SPACE=$(df / | tail -1 | awk '{print $4}')  # in KB
AVAILABLE_GB=$((AVAILABLE_SPACE / 1024 / 1024))

echo "Available disk space: ${AVAILABLE_GB}GB"

# Determine swap size based on available space
if [ $AVAILABLE_GB -ge 12 ]; then
    SWAP_SIZE="8G"
    SWAP_SIZE_MB=8192
    print_status "Creating 8GB swap (sufficient disk space)"
elif [ $AVAILABLE_GB -ge 8 ]; then
    SWAP_SIZE="4G"
    SWAP_SIZE_MB=4096
    print_warning "Creating 4GB swap (limited disk space)"
elif [ $AVAILABLE_GB -ge 5 ]; then
    SWAP_SIZE="2G"
    SWAP_SIZE_MB=2048
    print_warning "Creating 2GB swap (very limited disk space)"
else
    print_error "Insufficient disk space (${AVAILABLE_GB}GB available)"
    print_error "Need at least 5GB free. Please expand your disk or free up space."
    exit 1
fi

# Check if swap already exists
if swapon --show | grep -q '/swapfile'; then
    print_warning "Swap file already exists. Skipping swap creation."
else
    # Try fallocate first, fall back to dd if it fails
    print_status "Creating ${SWAP_SIZE} swap file..."

    if ! sudo fallocate -l ${SWAP_SIZE} /swapfile 2>/dev/null; then
        print_warning "fallocate failed, using dd method (slower but more reliable)..."
        sudo dd if=/dev/zero of=/swapfile bs=1M count=${SWAP_SIZE_MB} status=progress || {
            print_error "Failed to create swap file. Disk may be full."
            exit 1
        }
    fi

    # Set correct permissions
    sudo chmod 600 /swapfile

    # Mark the file as swap space
    sudo mkswap /swapfile

    # Enable the swap file
    sudo swapon /swapfile

    # Make swap permanent by adding to /etc/fstab
    if ! grep -q '/swapfile' /etc/fstab; then
        echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
    fi

    # Optimize swappiness (reduce swap usage preference)
    if ! grep -q 'vm.swappiness' /etc/sysctl.conf; then
        echo 'vm.swappiness=10' | sudo tee -a /etc/sysctl.conf
    fi
    sudo sysctl vm.swappiness=10

    print_status "${SWAP_SIZE} Swap created and enabled"
fi

# Verify swap
print_status "Current memory status:"
free -h

###############################################################################
# 3. Install Docker
###############################################################################
echo -e "\n${YELLOW}Step 3: Installing Docker...${NC}"

# Check if Docker is already installed
if command -v docker &> /dev/null; then
    print_warning "Docker is already installed ($(docker --version))"
else
    # Detect OS (Ubuntu or Debian)
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        OS_ID=$ID
    else
        print_error "Cannot detect OS. /etc/os-release not found."
        exit 1
    fi

    # Set Docker repository URL based on OS
    if [ "$OS_ID" = "ubuntu" ]; then
        DOCKER_REPO_URL="https://download.docker.com/linux/ubuntu"
        GPG_FILE="docker.gpg"
    elif [ "$OS_ID" = "debian" ]; then
        DOCKER_REPO_URL="https://download.docker.com/linux/debian"
        GPG_FILE="docker.asc"
    else
        print_error "Unsupported OS: $OS_ID. This script supports Ubuntu and Debian only."
        exit 1
    fi

    print_status "Detected OS: $OS_ID"

    # Install prerequisites
    sudo apt-get install -y \
        ca-certificates \
        curl \
        gnupg \
        lsb-release

    # Add Docker's official GPG key
    sudo install -m 0755 -d /etc/apt/keyrings

    if [ "$OS_ID" = "debian" ]; then
        # Debian uses .asc format
        sudo curl -fsSL ${DOCKER_REPO_URL}/gpg -o /etc/apt/keyrings/${GPG_FILE}
        sudo chmod a+r /etc/apt/keyrings/${GPG_FILE}
    else
        # Ubuntu uses .gpg format
        curl -fsSL ${DOCKER_REPO_URL}/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/${GPG_FILE}
        sudo chmod a+r /etc/apt/keyrings/${GPG_FILE}
    fi

    # Set up the Docker repository
    echo \
      "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/${GPG_FILE}] ${DOCKER_REPO_URL} \
      $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

    # Install Docker Engine
    sudo apt-get update -y
    sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

    print_status "Docker installed successfully"
fi

###############################################################################
# 4. Configure Docker for non-root user
###############################################################################
echo -e "\n${YELLOW}Step 4: Configuring Docker permissions...${NC}"

# Add current user to docker group
if groups $USER | grep &>/dev/null '\bdocker\b'; then
    print_warning "User $USER is already in docker group"
else
    sudo usermod -aG docker $USER
    print_status "User $USER added to docker group"
    print_warning "You need to log out and log back in for group changes to take effect!"
fi

# Start and enable Docker service
sudo systemctl start docker
sudo systemctl enable docker
print_status "Docker service started and enabled"

###############################################################################
# 5. Verify Installation
###############################################################################
echo -e "\n${YELLOW}Step 5: Verifying installation...${NC}"

# Check Docker version
DOCKER_VERSION=$(docker --version)
print_status "Docker: $DOCKER_VERSION"

# Check Docker Compose version
COMPOSE_VERSION=$(docker compose version)
print_status "Docker Compose: $COMPOSE_VERSION"

# Check swap
SWAP_INFO=$(swapon --show)
print_status "Swap status:\n$SWAP_INFO"

###############################################################################
# 6. Docker daemon optimization for 8GB RAM
###############################################################################
echo -e "\n${YELLOW}Step 6: Optimizing Docker daemon for limited memory...${NC}"

# Create Docker daemon config if it doesn't exist
sudo mkdir -p /etc/docker

# Configure Docker daemon with memory-optimized settings
sudo tee /etc/docker/daemon.json > /dev/null <<EOF
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  },
  "storage-driver": "overlay2",
  "default-ulimits": {
    "nofile": {
      "Name": "nofile",
      "Hard": 64000,
      "Soft": 64000
    }
  }
}
EOF

# Restart Docker to apply changes
sudo systemctl restart docker
print_status "Docker daemon configured for optimized memory usage"

###############################################################################
# 7. Install additional useful tools
###############################################################################
echo -e "\n${YELLOW}Step 7: Installing additional tools...${NC}"
sudo apt-get install -y \
    htop \
    git \
    curl \
    wget \
    vim \
    net-tools

print_status "Additional tools installed"

###############################################################################
# 8. Final Summary
###############################################################################
echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}Installation Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
print_status "System memory: $(free -h | grep Mem | awk '{print $2}')"
print_status "Swap memory: $(free -h | grep Swap | awk '{print $2}')"
print_status "Docker version: $DOCKER_VERSION"
print_status "Docker Compose version: $COMPOSE_VERSION"
echo ""
print_warning "IMPORTANT: Log out and log back in for Docker group changes to take effect!"
print_warning "After re-login, verify with: docker run hello-world"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo "1. Log out and log back in (or run: newgrp docker)"
echo "2. Clone your repository: git clone <your-repo-url>"
echo "3. Navigate to project: cd onmeet-backend"
echo "4. Copy .env.example to .env and configure your environment variables"
echo "5. Build images: ./gradlew jibDockerBuild --parallel"
echo "6. Start services: docker compose up -d"
echo ""
print_status "Setup script finished successfully!"
