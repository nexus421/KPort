# KPort

A professional, lightweight TCP and UDP port forwarder built with **Kotlin/Native**, specifically optimized for **Linux
** environments.

This project is a private utility designed for efficient port forwarding, successfully utilized for SSH tunneling and
similar networking tasks. By leveraging Kotlin/Native, KPort provides high performance and a minimal footprint without
requiring a virtual machine.

> **Note:** KPort is primarily developed and tested for Linux. Support for Windows and macOS is currently not a
> priority.

## Features

- **High Performance**: Built with Kotlin/Native for native execution.
- **TCP Forwarding**: Seamless data piping between local and remote endpoints.
- **UDP Forwarding**: Efficient packet handling with session management and automatic cleanup.
- **Systemd Integration**: Built-in support for service installation and management.
- **Configuration-based**: Simplified setup via a `config.json` file.

## Configuration

KPort manages its configuration at `~/.config/kport/config.json`. A default configuration is generated upon the first
execution.

### Example `config.json`

```json
{
  "debug": true,
  "rules": [
    {
      "portFrom": 8080,
      "portTo": 80,
      "ipTo": "127.0.0.1",
      "type": "TCP"
    },
    {
      "portFrom": 5353,
      "portTo": 53,
      "ipTo": "8.8.8.8",
      "type": "UDP"
    }
  ]
}
```

- `debug`: Enables verbose logging.
- `rules`: List of forwarding definitions.
    - `portFrom`: Local listener port.
    - `portTo`: Remote target port.
    - `ipTo`: Target IP address.
    - `type`: Protocol type (`"TCP"` or `"UDP"`).

## Service Management

KPort includes integrated commands to manage itself as a Linux systemd service.

### Installation

To set up KPort as a system service, run:

```bash
sudo ./KPort.kexe --createService
```

The installer will prompt for a working directory and the user context under which the service should operate.

### Removal

To remove the service and its systemd configuration:

```bash
sudo ./KPort.kexe --removeService
```

## Building

Requires JDK for the Gradle build process.

```bash
./gradlew build
```

The executable is located in `build/bin/native/releaseExecutable/`.

## Running

```bash
./KPort.kexe
```

### Privileged Ports (Ports < 1024)

Binding to privileged ports on Linux requires elevated permissions:

1. **Direct Execution**:
   ```bash
   sudo ./KPort.kexe
   ```
2. **Capability Grant** (Recommended):
   ```bash
   sudo setcap 'cap_net_bind_service=+ep' ./KPort.kexe
   ./KPort.kexe
   ```

## License

WTFPL – Do What the Fuck You Want to Public License
