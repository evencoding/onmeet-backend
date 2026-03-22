#!/bin/bash

###############################################################################
# Create 4GB Swap (Alternative for low disk space)
###############################################################################

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

print_status() {
    echo -e "${GREEN}[✓]${NC} $1"
}

print_error() {
    echo -e "${RED}[✗]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[!]${NC} $1"
}

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Creating 4GB Swap Memory${NC}"
echo -e "${GREEN}========================================${NC}"

# Check available disk space
AVAILABLE_SPACE=$(df / | tail -1 | awk '{print $4}')
REQUIRED_SPACE=$((4 * 1024 * 1024))  # 4GB in KB

echo "Available disk space: $(numfmt --to=iec-i --suffix=B $((AVAILABLE_SPACE * 1024)))"
echo "Required space: 4GB"

if [ $AVAILABLE_SPACE -lt $REQUIRED_SPACE ]; then
    print_error "Not enough disk space. Need at least 4GB free."
    print_warning "Please free up disk space or expand your disk."
    exit 1
fi

# Check if swap already exists
if swapon --show | grep -q '/swapfile'; then
    print_warning "Swap file already exists. Removing old swap..."
    sudo swapoff /swapfile 2>/dev/null || true
    sudo rm -f /swapfile
fi

# Create 4GB swap file using dd (more reliable for low disk space)
print_status "Creating 4GB swap file (this may take 1-2 minutes)..."
sudo dd if=/dev/zero of=/swapfile bs=1M count=4096 status=progress

# Set correct permissions
sudo chmod 600 /swapfile
print_status "Set swap file permissions"

# Mark the file as swap space
sudo mkswap /swapfile
print_status "Initialized swap file"

# Enable the swap file
sudo swapon /swapfile
print_status "Swap enabled"

# Make swap permanent
if ! grep -q '/swapfile' /etc/fstab; then
    echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
    print_status "Added swap to /etc/fstab"
fi

# Optimize swappiness
if ! grep -q 'vm.swappiness' /etc/sysctl.conf; then
    echo 'vm.swappiness=10' | sudo tee -a /etc/sysctl.conf
fi
sudo sysctl vm.swappiness=10
print_status "Set swappiness to 10"

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Swap Created Successfully!${NC}"
echo -e "${GREEN}========================================${NC}"
free -h
swapon --show