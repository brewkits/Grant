# Contributing to Grant 🎯

Thank you for your interest in contributing to Grant! We welcome contributions from the community.

## 🚀 Getting Started

### Prerequisites

- **Kotlin**: Version 2.1.0 or higher
- **Android Studio**: Latest stable version (for Android development)
- **Xcode**: Latest stable version (for iOS development)
- **JDK**: Version 17 or higher

### Setup Development Environment

1. **Clone the repository**
   ```bash
   git clone https://github.com/brewkits/Grant.git
   cd Grant
   ```

2. **Open in Android Studio**
   - Open the project in Android Studio
   - Let Gradle sync complete
   - Wait for indexing to finish

3. **Run tests**
   ```bash
   ./gradlew test
   ```

## 📝 How to Contribute

### Reporting Bugs

Before creating a bug report, please:
- Check the [existing issues](https://github.com/brewkits/Grant/issues) to avoid duplicates
- Verify the issue on the latest version
- Include as much detail as possible

**Bug Report Template:**
```markdown
**Description**
A clear description of the bug

**To Reproduce**
Steps to reproduce the behavior:
1. ...
2. ...

**Expected Behavior**
What you expected to happen

**Actual Behavior**
What actually happened

**Environment**
- Grant version:
- Platform: Android/iOS
- OS version:
- Device:

**Additional Context**
Any other relevant information
```

### Suggesting Features

Feature suggestions are welcome! Please:
- Check if the feature already exists
- Provide a clear use case
- Explain why it would be useful to most users

### Pull Requests

1. **Fork the repository**

2. **Create a feature branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

3. **Make your changes**
   - Follow the existing code style
   - Add tests for new functionality
   - Update documentation as needed

4. **Run tests**
   ```bash
   ./gradlew test
   ./gradlew check
   ```

5. **Commit your changes**
   ```bash
   git commit -m "feat: Add your feature description"
   ```

   Follow [Conventional Commits](https://www.conventionalcommits.org/):
   - `feat:` - New feature
   - `fix:` - Bug fix
   - `docs:` - Documentation changes
   - `test:` - Adding or updating tests
   - `refactor:` - Code refactoring
   - `chore:` - Maintenance tasks

6. **Push to your fork**
   ```bash
   git push origin feature/your-feature-name
   ```

7. **Create a Pull Request**
   - Use a clear, descriptive title
   - Reference any related issues
   - Describe your changes in detail

## 💻 Code Guidelines

### Kotlin Style

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Keep functions small and focused
- Add KDoc comments for public APIs

### Testing

- Write unit tests for all new functionality
- Maintain or improve code coverage
- Use descriptive test names
- Test edge cases and error conditions

### Documentation

- Update README.md if adding new features
- Add KDoc comments for public APIs
- Update relevant documentation in `docs/` folder
- Include code examples where appropriate

## 🏗️ Project Structure

```
Grant/
├── grant-core/          # Core permission management
├── grant-compose/       # Compose UI integration
├── demo/                # Demo application
├── docs/                # Documentation
└── README.md            # Main documentation
```

## 🔍 Code Review Process

1. All submissions require review
2. Maintainers will review your PR
3. Address feedback promptly
4. Once approved, maintainers will merge

## 📄 License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.

## 🤝 Code of Conduct

### Our Pledge

We pledge to make participation in our project a harassment-free experience for everyone.

### Expected Behavior

- Be respectful and inclusive
- Accept constructive criticism gracefully
- Focus on what is best for the community
- Show empathy towards others

### Unacceptable Behavior

- Harassment, discrimination, or offensive comments
- Personal or political attacks
- Public or private harassment
- Publishing others' private information

## 📞 Contact

- **Issues**: [GitHub Issues](https://github.com/brewkits/Grant/issues)
- **Email**: datacenter111@gmail.com

## 🙏 Thank You

Thank you for contributing to Grant! Your efforts help make this library better for everyone.

---

**Made with ❤️ by the Grant community**
