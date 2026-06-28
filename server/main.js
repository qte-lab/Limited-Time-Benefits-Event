const express = require('express');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3001;

// Set correct Content-Type for .flac files
app.use((req, res, next) => {
    if (req.url.endsWith('.flac')) {
        res.setHeader('Content-Type', 'audio/flac');
    }
    next();
});

// Serve static files from the parent directory (root of the repo)
app.use(express.static(path.join(__dirname, '..')));

app.get('/api/activities', (_req, res) => {
    const fs = require('fs');
    const path = require('path');
    
    const dataPath = path.join(__dirname, 'data', 'activities.json');
    
    fs.readFile(dataPath, 'utf8', (err, data) => {
        if (err) {
            console.error('Failed to read activities data:', err);
            return res.status(500).json({
                success: false,
                message: 'Failed to read activities data'
            });
        }
        
        try {
            const jsonData = JSON.parse(data);
            const activities = jsonData.activities || [];
            
            res.json({
                success: true,
                data: activities
            });
        } catch (parseErr) {
            console.error('Failed to parse activities data:', parseErr);
            res.status(500).json({
                success: false,
                message: 'Failed to parse activities data'
            });
        }
    });
});

app.get('/api/download_apk', async (_req, res) => {
    const fs = require('fs');
    const path = require('path');
    
    const apkPath = path.join(__dirname, 'apk');
    
    try {
        const files = await fs.promises.readdir(apkPath);
        const apks = files.filter(file => file.endsWith('.apk'));
        
        // Read output-metadata.json to get APK metadata
        let latestApk = null;
        let latestApkSize = null;
        let versionCode = 0;
        let versionName = 'unknown';
        
        try {
            const metaPath = path.join(apkPath, 'output-metadata.json');
            const metaContent = await fs.promises.readFile(metaPath, 'utf8');
            const metaData = JSON.parse(metaContent);
            
            if (metaData.elements && metaData.elements.length > 0) {
                const mainElement = metaData.elements[0];
                latestApk = mainElement.outputFile || 'app-release.apk';
                versionCode = mainElement.versionCode || 0;
                versionName = mainElement.versionName || 'unknown';
            }
        } catch (err) {
            console.error('Failed to read output-metadata.json:', err);
            latestApk = 'app-release.apk';
        }
        
        // Get file size of latest APK
        if (latestApk) {
            try {
                const stats = await fs.promises.stat(path.join(apkPath, latestApk));
                latestApkSize = (stats.size / (1024 * 1024)).toFixed(1);
            } catch (err) {
                console.error('Failed to get APK file size:', err);
            }
        }
        
        // Get changelog data
        let changelogData = {};
        try {
            const changelogPath = path.join(__dirname, 'data', 'changelog.json');
            const changelogJson = await fs.promises.readFile(changelogPath, 'utf8');
            changelogData = JSON.parse(changelogJson);
        } catch (err) {
            console.error('Failed to read changelog:', err);
        }
        
        res.json({
            success: true,
            data: apks,
            latest: latestApk,
            latestSize: latestApkSize,
            versionCode,
            versionName,
            changelog: changelogData.changelog || {}
        });
    } catch (err) {
        console.error('Failed to read APK directory:', err);
        res.status(500).json({
            success: false,
            message: 'Failed to read APK directory'
        });
    }
});

// Download APK file by filename
app.get('/api/download_apk/:filename', (req, res) => {
    const fs = require('fs');
    const path = require('path');
    
    const filename = req.params.filename;
    const apkPath = path.join(__dirname, 'apk', filename);
    
    // Validate filename ends with .apk
    if (!filename.endsWith('.apk')) {
        return res.status(400).json({
            success: false,
            message: 'Invalid file type'
        });
    }
    
    // Use fs.promises to check if file exists
    fs.promises.access(apkPath, fs.constants.F_OK)
        .then(() => {
            // Get file stats
            return fs.promises.stat(apkPath);
        })
        .then((stats) => {
            // Set response headers
            res.setHeader('Content-Type', 'application/vnd.android.package-archive');
            res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);
            res.setHeader('Content-Length', stats.size);
            res.setHeader('Cache-Control', 'no-cache');
            
            // Use streaming to transfer file
            const stream = fs.createReadStream(apkPath);
            
            // Handle stream errors
            stream.on('error', (err) => {
                console.error('Failed to read APK file:', err);
                if (!res.headersSent) {
                    res.status(500).json({
                        success: false,
                        message: 'Failed to read APK file'
                    });
                }
            });
            
            // Handle client aborted connection
            req.on('aborted', () => {
                stream.destroy();
            });
            
            // Pipe stream to response
            stream.pipe(res);
        })
        .catch((err) => {
            if (err.code === 'ENOENT') {
                return res.status(404).json({
                    success: false,
                    message: 'File not found'
                });
            }
            console.error('Failed to download APK file:', err);
            res.status(500).json({
                success: false,
                message: 'Failed to download APK file'
            });
        });
});

// Get list of outdate test markdown files
app.get('/api/outdate-test/markdown', (_req, res) => {
    const fs = require('fs');
    const path = require('path');
    
    const dataPath = path.join(__dirname, 'data', 'outdate-test-markdown');
    
    fs.readdir(dataPath, (err, files) => {
        if (err) {
            console.error('Failed to read markdown files directory:', err);
            return res.status(500).json({
                success: false,
                message: 'Failed to read markdown files directory'
            });
        }
        
        // Filter out .md files and sort by filename descending
        const markdownFiles = files.filter(file => file.endsWith('.md')).sort((a, b) => b.localeCompare(a));
        
        res.json({
            success: true,
            data: markdownFiles
        });
    });
});

// Get content of specific markdown file
app.get('/api/outdate-test/markdown/:filename', (req, res, next) => {
    const fs = require('fs');
    const path = require('path');
    
    const filename = req.params.filename;
    const filePath = path.join(__dirname, 'data', 'outdate-test-markdown', filename);
    
    // Validate file exists and is .md file
    if (!filename.endsWith('.md')) {
        // For non-md files, let static file serving handle
        return next();
    }
    
    // Use fs.promises to read file content
    fs.promises.readFile(filePath, 'utf8')
        .then((content) => {
            res.json({
                success: true,
                data: {
                    filename: filename,
                    content: content
                }
            });
        })
        .catch((err) => {
            if (err.code === 'ENOENT') {
                return res.status(404).json({
                    success: false,
                    message: 'File not found'
                });
            }
            console.error('Failed to read markdown file:', err);
            res.status(500).json({
                success: false,
                message: 'Failed to read markdown file'
            });
        });
});


app.listen(PORT, '0.0.0.0', () => {
    console.log(`Server started, accessible at: http://0.0.0.0:${PORT}`);
    console.log(`Local access: http://localhost:${PORT}`);
});

app.use((err, _req, res, _next) => {
    console.error('Server error:', err);
    res.status(500).json({
        success: false,
        message: 'Server internal error',
        error: err.message
    });
});
