package cbs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CBS Nove — main application entry point.
 *
 * <h3>Kotlin Scripting Intent (architectural note)</h3>
 * <p>
 * This application is designed to expose selected Spring beans (e.g. JPA
 * repositories, domain services) to a Kotlin {@code ScriptEngine} /
 * {@code KotlinScriptHost} at runtime.  The goal is to allow platform
 * operators to write and execute ad-hoc {@code .kts} scripts against live
 * application state — for example, running diagnostic queries, back-filling
 * data, or triggering batch operations — without deploying new code.
 * </p>
 * <p>
 * The scripting engine itself is <b>not</b> implemented in this initial
 * commit.  The required pieces are:
 * </p>
 * <ul>
 *   <li>A {@code KotlinScriptHost} bean that creates a
 *       {@code ScriptEngineManager}-backed Kotlin JSR-223 engine.</li>
 *   <li>A binding layer that injects whitelisted Spring beans into the
 *       script scope (repositories, services, DTOs).</li>
 *   <li>A secure evaluation sandbox with time-outs, memory limits, and
 *       role-based access control (only admins may execute scripts).</li>
 * </ul>
 *
 * @see <a href="https://kotlinlang.org/docs/jsr223.html">Kotlin JSR-223</a>
 */
@SpringBootApplication
public class CbsApp {

    public static void main(String[] args) {
        SpringApplication.run(CbsApp.class, args);
    }
}
