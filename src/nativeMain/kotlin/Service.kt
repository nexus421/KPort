import kotlinx.cinterop.*
import platform.posix.PATH_MAX
import platform.posix.geteuid
import platform.posix.readlink
import platform.posix.system
import kotlin.system.exitProcess

/**
 * Removes the 'kport' system service.
 *
 * This method stops and disables the 'kport' service using `systemctl`, deletes its service file,
 * and reloads the systemd daemon to apply changes. To perform this action, the method requires root permissions.
 * If the process is not run as root, it will terminate with an error message.
 *
 * Note: The method does not delete the service's working directory. Deletion of the working directory
 * must be performed manually by the user.
 */
fun removeService() {
    if (geteuid() != 0u) {
        println("Please run as root to create a service!")
        exitProcess(1)
    }
    system("sudo systemctl stop kport && sudo systemctl disable kport && sudo rm /etc/systemd/system/kport.service && sudo systemctl daemon-reload")

    println("Service 'kport' removed successfully.")
    println("Please delete the working directory manually.")
}

/**
 * Creates and configures a new systemd service for the application.
 *
 * This method is designed for creating a Linux systemd service for the KPort application.
 * It performs the following operations:
 * - Ensures the method is run with superuser privileges. If not, the program exits.
 * - Requests and validates a working directory, creating it if necessary.
 * - Creates a run script within the specified working directory.
 * - Validates the existence of the application executable, ensuring it's located within the working directory.
 *   If the executable is found in a different location, it is copied to the working directory,
 *   renamed to "KPort.kexe" (if necessary), and made executable.
 * - Prompts for a username under which the service should run. Defaults to "root" if no input is provided.
 * - Generates a systemd service file in `/etc/systemd/system/` with the necessary configuration
 *   to run the application as a service.
 * - Reloads systemd daemons, enables the service to start on boot, and starts the new service.
 * - Creates helper scripts (`status.sh`, `restart.sh`) in the working directory for managing the service.
 * - Sets proper ownership and permissions on the working directory and its contents.
 *
 * Key configurations for the systemd service include:
 * - `WorkingDirectory`: Path specified by the user where the service operates.
 * - `User`: User under which the service runs.
 * - `ExecStart`: Path to the run script that starts the application executable.
 * - Automatic restart with a fixed delay (`Restart=always`, `RestartSec=10`).
 *
 * Preconditions:
 * - The application should be run with root privileges (`geteuid() == 0`).
 * - Proper path to the KPort executable should be provided or manually placed in the working directory.
 *
 * Postconditions:
 * - A Linux systemd service is created, enabled, and started for the KPort application.
 * - Helper scripts for managing the service are available in the specified working directory.
 *
 * Note: This method relies on external system commands (`systemctl`, `chmod`, etc.),
 * which may fail if the necessary tools are not available or configured correctly in the environment.
 */
@OptIn(ExperimentalForeignApi::class)
fun createService() {
    if (geteuid() != 0u) {
        println("Please run as root to create a service!")
        exitProcess(1)
    }

    val serviceName = "kport"
    val description = "KPort - Linux port forwarding service. Built with Kotlin/Native."

    print("Working Directory (absolute path, will be created if it doesn't exist): ")
    val workingDir = readlnOrNull() ?: return

    //Create working dir.
    val workingDirFile = OkioFile(workingDir)
    workingDirFile.mkdirs()

    //Create run script for service.
    val runScriptFile = OkioFile(workingDirFile, "run.sh")
    runScriptFile.createNewFile()
    runScriptFile.writeText("#!/bin/bash\n./KPort.kexe")
    val execStart = runScriptFile.absolutePath

    //Make sure the executable file is withing the working dir.
    getExecutablePath()?.let {
        val executableFile = OkioFile(it)
        val executableFileInDestination = OkioFile(workingDirFile, executableFile.name)
        //If executable does not exist in working directory, copy it.
        if (executableFileInDestination.exists().not()) {
            executableFile.copy(workingDirFile)
            //if executable is not called KPort.kexe, rename it.
            if (executableFile.name != "KPort.kexe") {
                executableFileInDestination.renameTo("KPort.kexe")
            }
            system("chmod +x ${workingDirFile.path}/KPort.kexe")
        }
    } ?: println("KPort executable not found. Please copy it to the working directory and rename it to KPort.kexe.")

    print("User (the user the service should run under): ")
    val serviceUser = readlnOrNull() ?: "root"

    val serviceFile = NativeFile("/etc/systemd/system/$serviceName.service")
    val serviceContent = """
        |[Unit]
        |Description=$description
        |After=network.target
        |
        |[Service]
        |Type=simple
        |User=$serviceUser
        |WorkingDirectory=$workingDir
        |ExecStart=$execStart
        |Restart=always
        |RestartSec=10
        |
        |[Install]
        |WantedBy=multi-user.target
    """.trimMargin()

    serviceFile.writeText(serviceContent)

    system("systemctl daemon-reload")
    system("systemctl enable $serviceName")
    system("systemctl start $serviceName")

    val statusScript = workingDirFile / "status.sh"
    val restartScript = workingDirFile / "restart.sh"

    statusScript.writeText("#!/bin/bash\nsystemctl status $serviceName\n")
    restartScript.writeText("#!/bin/bash\nsudo systemctl restart $serviceName\n")

    system("chmod +x ${statusScript.path} ${restartScript.path} ${runScriptFile.path}")
    system("chown -R $serviceUser:$serviceUser $workingDir")

    println("------------------------------------------------")
    println("Service '$serviceName' is now running as user '$serviceUser'.")
    println("Helper scripts are located in $workingDir.")
}

@OptIn(ExperimentalForeignApi::class)
private fun getExecutablePath(): String? = memScoped {
    val buffer = allocArray<ByteVar>(PATH_MAX)
    val bytes = readlink("/proc/self/exe", buffer, PATH_MAX.toULong())
    if (bytes == -1L) return null
    buffer[bytes] = 0.toByte() // Null-Terminator setzen
    buffer.toKString()
}