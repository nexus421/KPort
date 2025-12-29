# KPort

A simple and lightweight TCP and UDP port forwarder written in Kotlin Native.

## Features

- **TCP Forwarding**: Pipes data between a local port and a remote target.
- **UDP Forwarding**: Handles UDP packets with session management and automatic cleanup of inactive sessions.
- **Multi-platform**: Built with Kotlin Multiplatform (currently targeting Linux x64 and Arm64).
- **Configuration-based**: Easy to set up using a `config.json` file.

## Configuration

KPort looks for a configuration file at `~/.config/kport/config.json`. If it doesn't exist, it creates a default one.

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

- `debug`: Enables detailed logging.
- `rules`: A list of forwarding rules.
    - `portFrom`: The local port to listen on.
    - `portTo`: The target port to forward to.
    - `ipTo`: The target IP address.
    - `type`: Either `"TCP"` or `"UDP"`.

## Building

To build the project, you need the JDK installed.

```bash
./gradlew build
```

The executable will be located in `build/bin/native/releaseExecutable/` (or `debugExecutable/`).

## Running

Simply run the generated binary:

```bash
./build/bin/native/releaseExecutable/KPort.kexe
```

### Privileged Ports (Ports < 1024)

On Linux, binding to ports below 1024 (e.g., 80, 443, 422) requires root privileges. You can either:

1. **Run with sudo**:
   ```bash
   sudo ./build/bin/native/releaseExecutable/KPort.kexe
   ```
2. **Grant capabilities** (recommended for security):
   ```bash
   sudo setcap 'cap_net_bind_service=+ep' ./build/bin/native/releaseExecutable/KPort.kexe
   ./build/bin/native/releaseExecutable/KPort.kexe
   ```

## Testing

### TCP Testing

You can use `nc` (netcat) to test TCP forwarding.

1. Start a listener on the target port (e.g., 8000):
   ```bash
   nc -l -p 8000
   ```
2. Configure KPort to forward from 9000 to 8000.
3. Send data to port 9000:
   ```bash
   echo "Hello TCP" | nc localhost 9000
   ```
4. You should see "Hello TCP" in the listener's output.

### UDP Testing

1. Start a UDP listener on the target port (e.g., 8000):
   ```bash
   nc -u -l -p 8000
   ```
2. Configure KPort to forward from 9000 to 8000 (type: UDP).
3. Send data to port 9000:
   ```bash
   echo "Hello UDP" | nc -u localhost 9000
   ```
4. You should see "Hello UDP" in the listener's output.

### Using Curl

If forwarding to an HTTP server:
```bash
curl http://localhost:9000
```
