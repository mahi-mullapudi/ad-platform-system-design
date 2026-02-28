#!/bin/bash
echo "========================================="
echo "GitHub Push Helper"
echo "========================================="
echo ""
echo "Step 1: Opening GitHub to create a Personal Access Token..."
echo ""
# Open the token creation page
open "https://github.com/settings/tokens/new?description=Ad%20Platform%20System%20Design&scopes=repo"
echo "In your browser:"
echo "  1. Make sure 'repo' scope is checked ✓"
echo "  2. Click 'Generate token'"
echo "  3. Copy the token (starts with ghp_...)"
echo ""
echo "Step 2: Enter your token when prompted below..."
echo "Press Enter to continue..."
read
echo ""
echo "Now attempting to push..."
echo ""
# Use git credential fill to properly handle the token
git push -u origin main
echo ""
echo "Done!"
