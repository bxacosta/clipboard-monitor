# GitHub Actions Deployment Guide

This project uses GitHub Actions for CI/CD and automatically publishes to **GitLab Package Registry**.

## Available Workflows

### 1. `ci.yml` - CI Pipeline

**Trigger:** Push to `main` and Pull Requests to `main`

- Runs tests automatically
- Generates test reports with JUnit format
- Uses Java 21 with Temurin distribution

### 2. `release.yml` - Release and Publish

**Trigger:** Push tags with format `v*` (e.g., `v1.0.0`, `v1.2.3`) or manual dispatch

- Determines version from tag or manual input
- Builds project with specified version
- Publishes to GitLab Package Registry
- Creates GitHub Release with JAR artifacts

## How to Create a Release

### Recommended Method (Automatic with Tags)

1. **Commit your changes:**
   ```bash
   git add .
   git commit -m "feat: new functionality"
   git push origin main
   ```

2. **Create and push version tag:**
   ```bash
   # Create local tag
   git tag v1.0.0

   # Push tag (this triggers automatic deployment)
   git push origin v1.0.0
   ```

3. **The release workflow automatically:**
    - Extracts version from tag (removes 'v' prefix)
    - Builds project with the release version
    - Publishes to GitLab Package Registry
    - Creates GitHub Release with JAR files

### Manual Method

1. Go to "Actions" tab in GitHub
2. Select "Release and Publish"
3. Click "Run workflow"
4. Optionally specify a version (if not provided, it will use tag version)
5. Click "Run workflow" button

## Required Configuration

### GitHub Secret (Settings → Secrets and variables → Actions)

You only need to configure **ONE** secret:

```
GITLAB_DEPLOY_TOKEN=your_gitlab_deploy_token_here
```

**Where to get the GITLAB_DEPLOY_TOKEN?**

1. Go to your project in GitLab
2. Settings → Repository → Deploy tokens
3. Create a new token with scopes: `read_package_registry`, `write_package_registry`
4. Copy the generated token

### GitLab Project ID

Update the project ID in `build.gradle` (line 93):

```groovy
url = uri('https://gitlab.com/api/v4/projects/70108742/packages/maven')
```

Find your project ID in: GitLab → Settings → General

### Optional Environment Variables

In `gradle.properties` (for local development):

```properties
gitlabDeployToken=your_gitlab_deploy_token
```

## Versioning Conventions

This project follows [Semantic Versioning](https://semver.org/):

- **v1.0.0** - Major release
- **v1.1.0** - New functionality (minor)
- **v1.0.1** - Bug fixes (patch)
- **v2.0.0-beta.1** - Pre-release

## Troubleshooting

### Error: "Could not publish to GitLab"

- Verify that `GITLAB_DEPLOY_TOKEN` is configured in GitHub Secrets
- Verify that the token has `read_package_registry` and `write_package_registry` permissions
- Verify that the token has not expired
- Check that the GitLab project ID in `build.gradle` is correct

### Error: "Version already exists"

- You cannot publish the same version twice to GitLab Package Registry
- Use a new version number or delete the existing version in GitLab
- Make sure you're incrementing the version properly

### Error: "Tests failed"

- Release deployment is cancelled if tests fail
- Check the CI Pipeline logs to see which test failed
- Fix the failing tests before attempting to release

### Error: "Invalid tag format"

- Tags must start with 'v' followed by semantic version
- Use format: `v1.0.0`, `v2.1.3`, etc.
- Avoid formats like `release-1.0` or `1.0.0`

## Using the Published Library

### Gradle

```gradle
repositories {
    maven {
        url = uri('https://gitlab.com/api/v4/projects/70108742/packages/maven')
        credentials(HttpHeaderCredentials) {
            name = 'Private-Token'
            value = 'YOUR_GITLAB_TOKEN'
        }
        authentication {
            header(HttpHeaderAuthentication)
        }
    }
}

dependencies {
    implementation 'dev.bxlab.clipboard:clipboard-monitor:1.0.0'
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>gitlab</id>
        <url>https://gitlab.com/api/v4/projects/70108742/packages/maven</url>
    </repository>
</repositories>

<dependency>
    <groupId>dev.bxlab.clipboard</groupId>
    <artifactId>clipboard-monitor</artifactId>
    <version>1.0.0</version>
</dependency>
```
