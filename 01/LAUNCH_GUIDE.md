# Launch Guide - Desktop & Web

## ✅ Fixed Issues
- ✅ Updated Compose version from 1.7.1 to 1.7.3 (Kotlin 2.1.0 compatibility)
- ✅ Simplified WasmJs configuration (removed DevServer complexity)
- ✅ Fixed String.format() for cross-platform compatibility
- ✅ All targets compile successfully

## 🖥️ Desktop Launch

### Quick Start
```bash
./gradlew :composeApp:runDesktop
```

Or shorter:
```bash
./gradlew run
```

### Expected Output
```
> Task :composeApp:runDesktop
BUILD SUCCESSFUL in 5s
```
→ Desktop window opens automatically

### Desktop Commands Reference
```bash
# Run the app
./gradlew :composeApp:runDesktop

# Build JAR only (no run)
./gradlew :composeApp:desktopJar

# Create distributable installer
./gradlew :composeApp:packageDistributionForCurrentOS

# Run with debug logs
./gradlew :composeApp:runDesktop --info
```

## 🌐 Web Launch

### Development Server (Recommended)
```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

This will:
1. ✅ Build the WasmJs target
2. ✅ Start webpack dev server
3. ✅ Automatically copy index.html to output directory
4. ✅ Server starts (usually on port 8082)

### Expected Output
```
> Task :composeApp:wasmJsBrowserDevelopmentRun
<i> [webpack-dev-server] Project is running at:
<i> [webpack-dev-server] Loopback: http://localhost:8082/
webpack 5.94.0 compiled with 1 warning in 576 ms
```
→ Open browser manually to http://localhost:8082

### Production Build
```bash
# Build optimized production bundle
./gradlew :composeApp:wasmJsBrowserProductionWebpack

# Output location:
# composeApp/build/dist/wasmJs/productionExecutable/
```

### Serve Production Build
```bash
cd composeApp/build/kotlin-webpack/wasmJs/productionExecutable
python3 -m http.server 8080
# Then open: http://localhost:8080
```

## 🔧 Troubleshooting

### Port Already in Use
```bash
# Dev server typically uses port 8082
# Find what's using the port
lsof -i :8082

# Kill the process
kill -9 <PID>

# Or wait for Gradle to automatically select another port
```

### "Permission Denied" Error
```bash
chmod +x gradlew
./gradlew clean
```

### Build Fails - Clean and Retry
```bash
./gradlew clean
./gradlew :composeApp:compileKotlinDesktop  # Test desktop
./gradlew :composeApp:compileKotlinWasmJs   # Test web
```

### Browser Shows Blank Page
1. Clear browser cache (Cmd+Shift+R / Ctrl+Shift+R)
2. Open browser DevTools (F12) → Check Console for errors
3. Rebuild: `./gradlew clean :composeApp:wasmJsBrowserDevelopmentRun`

### Credentials Not Found
```bash
# Check local.properties exists
cat local.properties

# Should show:
# gigachat.clientId=your_client_id
# gigachat.clientSecret=your_client_secret

# If missing, copy from template:
cp local.properties.template local.properties
# Then edit with your credentials
```

## 📊 Platform Comparison

| Feature | Desktop | Web |
|---------|---------|-----|
| **Launch Command** | `./gradlew run` | `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` |
| **Build Time (First)** | ~10-15 sec | ~20-30 sec |
| **Build Time (Cached)** | ~2-5 sec | ~5-10 sec |
| **Hot Reload** | ❌ No | ✅ Yes (dev mode) |
| **Native Feel** | ✅ Yes | ⚠️ Browser-based |
| **File Size** | ~50 MB | ~5 MB |
| **Distributable** | .dmg/.msi/.deb | Static files |
| **SSL Trust** | ✅ Configured | ✅ Configured |
| **Settings UI** | ✅ Works | ✅ Works |
| **All Features** | ✅ Full support | ✅ Full support |

## 🚀 Quick Launch Commands

### Desktop
```bash
# Just run it!
./gradlew run
```

### Web
```bash
# Development with hot reload
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

## ✨ What's Working

✅ **Desktop**
- Window opens correctly
- All UI components render
- Settings screen works
- AI chat functionality
- SSL trust configured

✅ **Web**
- Loads in browser
- Responsive UI
- Settings screen works
- AI chat functionality
- SSL trust configured

## 📝 Tips

### Desktop
- **First launch** takes longer (downloads dependencies)
- **Subsequent launches** are much faster (cached)
- **Window size** is 800x600 by default
- **Close** with Cmd+Q (Mac) or Alt+F4 (Windows/Linux)

### Web
- **Dev server** supports hot reload - edit code and see changes
- **Production builds** are optimized and smaller
- **Browser compatibility**: Chrome, Firefox, Safari (latest versions)
- **DevTools** (F12) helpful for debugging

## 🎯 Success Indicators

### Desktop Launch Success
```
BUILD SUCCESSFUL in 5s
```
✅ Window opens with "GigaChat" title
✅ Chat interface visible
✅ Settings button (⚙️) in toolbar

### Web Launch Success
```
webpack compiled successfully
i ｢wdm｣: Compiled successfully.
```
✅ Browser opens automatically
✅ Shows GigaChat interface
✅ No console errors

## 🎨 UI Features Available

Both platforms support:
- 💬 **Chat Screen**: Send messages, see history
- ⚙️ **Settings Screen**: Adjust AI parameters
- 🗑️ **Clear Chat**: Remove conversation history
- 🔄 **Error Retry**: Retry failed messages
- 📊 **AI Settings**: Temperature, Top P, Max Tokens, Repetition Penalty

## 🔐 Security

Both platforms include:
- ✅ SSL trust manager (for GigaChat endpoints)
- ✅ Secure credential handling (BuildKonfig)
- ✅ Proper HTTPS configuration

## 📚 Additional Resources

- **Project README**: `README.md`
- **AI Settings Guide**: `AI_SETTINGS.md`
- **Build Configuration**: `composeApp/build.gradle.kts`
- **Dependencies**: `gradle/libs.versions.toml`

---

**Ready to launch!** 🚀

Choose your platform:
- Desktop: `./gradlew run`
- Web: `./gradlew :composeApp:wasmJsBrowserDevelopmentRun`
