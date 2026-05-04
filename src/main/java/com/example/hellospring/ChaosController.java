package com.example.hellospring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationContext;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * A controller designed for Cloud-Native Chaos Engineering testing. Uses the HTML5
 * History API to prevent duplicate POSTs on reload without losing instance stickiness.
 */
@Controller
public class ChaosController {

	private static final Logger log = LoggerFactory.getLogger(ChaosController.class);

	private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

	private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

	private static final String COLOR_SUCCESS = "#28a745";

	private static final String COLOR_WARNING = "#ffc107";

	private static final String COLOR_DANGER = "#dc3545";

	private static final String TEXT_LIGHT = "#ffffff";

	private static final String TEXT_DARK = "#212529";

	@Value("${vcap.application.application_name:Local-Spring-App}")
	private String applicationName;

	@Value("${vcap.application.application_id:N/A}")
	private String applicationId;

	@Value("${CF_INSTANCE_INDEX:0}")
	private String instanceIndex;

	private final ApplicationContext context;

	private final ApplicationAvailability availability;

	private final TaskExecutor taskExecutor;

	private final FailReadyUntil failReadyUntil;

	public ChaosController(ApplicationContext context, TaskExecutor taskExecutor, ApplicationAvailability availability,
			FailReadyUntil failReadyUntil) {
		this.context = context;
		this.taskExecutor = taskExecutor;
		this.availability = availability;
		this.failReadyUntil = failReadyUntil;
	}

	@GetMapping("/")
	public String home(Model model) {
		return render(model, null);
	}

	@PostMapping("/fail/ready")
	public String triggerReadinessFailure(@RequestParam(defaultValue = "30") int duration, Model model) {
		int safeDuration = Math.max(1, Math.min(duration, 300));

		failReadyUntil.set(); // Mark the expected recovery time
		AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC);
		log.error("TRIGGERED READINESS FAILURE (Duration: {}s)", safeDuration);

		taskExecutor.execute(() -> {
			try {
				Thread.sleep(safeDuration * 1000L);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			failReadyUntil.clear();
			AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
			log.info("READINESS RECOVERED");
		});

		String msg = String.format("<strong>Readiness set to REFUSING</strong> at %s. Recovery in %ds.",
				LocalTime.now().format(TIME_FMT), safeDuration);
		return render(model, msg);
	}

	@PostMapping("/fail/live")
	public String triggerLivenessFailure(Model model) {
		log.error("TRIGGERED LIVENESS FAILURE - Awaiting platform restart...");
		AvailabilityChangeEvent.publish(context, LivenessState.BROKEN);

		String msg = String.format("<strong>Liveness set to BROKEN</strong> at %s. Expecting container restart.",
				LocalTime.now().format(TIME_FMT));
		return render(model, msg);
	}

	@PostMapping("/kill")
	public String triggerProcessKill(Model model) {
		log.warn("JVM KILL SIGNAL RECEIVED");

		taskExecutor.execute(() -> {
			try {
				Thread.sleep(2000);
				SpringApplication.exit(context, () -> 0);
				System.exit(0);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});

		model.addAttribute("backgroundColor", COLOR_DANGER);
		model.addAttribute("content",
				String.format(
						"""
								<div style="text-align:center; padding: 50px; font-family: sans-serif;">
								    <script>if(window.history.replaceState){window.history.replaceState(null,null,'/');}</script>
								    <h1 style="color:white;">SHUTTING DOWN</h1>
								    <p style="color:white; font-size: 1.2em;">Instance #%s is exiting now.</p>
								    <div style="margin-top:30px;">
								        <a href="/" style="color: white; font-weight: bold; text-decoration: underline;">Refresh Status</a>
								    </div>
								</div>
								""",
						instanceIndex));
		return "content";
	}

	private String render(Model model, String actionAlert) {
		LivenessState liveness = availability.getLivenessState();
		ReadinessState readiness = availability.getReadinessState();

		boolean isBroken = (liveness == LivenessState.BROKEN);
		boolean isRefusing = (readiness == ReadinessState.REFUSING_TRAFFIC);
		boolean anyFailure = isBroken || isRefusing;

		// Auto-populate actionAlert if a failure state is active but no specific message
		// was passed (e.g., on reload)
		if (actionAlert == null) {
			if (isBroken) {
				actionAlert = "<strong>Liveness is currently BROKEN</strong>. Awaiting platform recovery/restart.";
			}
			else if (isRefusing) {
				actionAlert = "<strong>Readiness is currently REFUSING traffic</strong> until approx "
						+ failReadyUntil.get().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
			}
		}

		String bgColor = COLOR_SUCCESS;
		String textColor = TEXT_LIGHT;
		if (isBroken)
			bgColor = COLOR_DANGER;
		else if (isRefusing) {
			bgColor = COLOR_WARNING;
			textColor = TEXT_DARK;
		}

		String alertHtml = (actionAlert != null) ? String.format(
				"<div style='background:rgba(255,255,255,0.2); padding:10px; margin-bottom:20px; border-radius:5px;'>%s</div>",
				actionAlert) : "";

		// Common Kill form snippet to reuse
		String killFormHtml = """
				<form action="/kill" method="POST" onsubmit="this.querySelector('button').disabled=true;" style="background:rgba(0,0,0,0.05); padding:15px; border-radius:8px;">
				    <strong>Process</strong><br/>
				    <button type="submit" style="background:#000; color:white;">Kill App</button>
				</form>
				""";

		String controlsHtml;
		if (anyFailure) {
			// If broken, still show the Kill form as requested
			String failureControls = isBroken ? String
				.format("<div style='margin-bottom:15px; display:flex; justify-content:center;'>%s</div>", killFormHtml)
					: "";

			controlsHtml = String.format("""
					<div style="margin-top:20px; border-top: 1px solid rgba(255,255,255,0.3); padding-top:20px;">
					    <p><em>Controls limited: System is in failure mode.</em></p>
					    %s
					</div>
					""", failureControls);
		}
		else {
			controlsHtml = String.format(
					"""
							<div style="margin-top:20px; border-top: 1px solid rgba(255,255,255,0.3); padding-top:20px; display: flex; gap: 10px; flex-wrap: wrap; justify-content: center;">
							    <form action="/fail/ready" method="POST" onsubmit="this.querySelector('button').disabled=true;" style="background:rgba(0,0,0,0.05); padding:15px; border-radius:8px;">
							        <strong>Readiness</strong><br/>
							        <input type="number" name="duration" value="30" style="width:50px; margin:5px 0;">s<br/>
							        <button type="submit">Refuse Traffic</button>
							    </form>
							    <form action="/fail/live" method="POST" onsubmit="this.querySelector('button').disabled=true;" style="background:rgba(0,0,0,0.05); padding:15px; border-radius:8px;">
							        <strong>Liveness</strong><br/>
							        <button type="submit" style="background:#800; color:white;">Break Liveness</button>
							    </form>
							    %s
							</div>
							""",
					killFormHtml);
		}

		String envSectionHtml = buildEnvVarsSection();

		String mainContent = String.format(
				"""
						<div style="max-width: 800px; margin: 40px auto; font-family: -apple-system, sans-serif; text-align:center;">
						    <script>if(window.history.replaceState){window.history.replaceState(null,null,'/');}</script>
						    %s
						    <h1 style="margin-bottom:0;">%s</h1>
						    <p style="font-size:1.5em; margin-top:10px;">Instance <strong>#%s</strong></p>
						    <div style="font-size: 0.9em; opacity: 0.8; margin-bottom:20px;">
						        ID: %s
						    </div>
						    %s
						    <div style="margin-top:30px;">
						        <a href="/" style="color: inherit; font-weight: bold; text-decoration: underline;">Refresh Status</a>
						    </div>
						    %s
						</div>
						""",
				alertHtml, applicationName, instanceIndex, applicationId, controlsHtml, envSectionHtml);

		model.addAttribute("backgroundColor", bgColor);
		model.addAttribute("textColor", textColor);
		model.addAttribute("content", mainContent);

		return "content";
	}

	private String buildEnvVarsSection() {
		StringBuilder sb = new StringBuilder();
		sb.append(
				"<div style=\"margin-top: 50px; border-top: 1px solid rgba(255,255,255,0.3); padding-top: 20px; text-align: left;\">");
		sb.append("<h3 style=\"text-align: center; margin-bottom: 20px;\">Environment Details</h3>");

		// VCAP_APPLICATION (Collapsible & Pretty JSON)
		String vcapApp = System.getenv("VCAP_APPLICATION");
		if (vcapApp != null) {
			sb.append(
					"<details style=\"background:rgba(0,0,0,0.1); padding:10px; margin-bottom:10px; border-radius:5px;\">");
			sb.append("<summary style=\"cursor:pointer; font-weight:bold;\">VCAP_APPLICATION</summary>");
			sb.append(
					"<pre style=\"white-space:pre-wrap; word-wrap:break-word; font-size:0.85em; margin-top:10px; overflow-x: auto;\">")
				.append(HtmlUtils.htmlEscape(prettifyJson(vcapApp)))
				.append("</pre>");
			sb.append("</details>");
		}

		// VCAP_SERVICES (Collapsible & Pretty JSON)
		String vcapServices = System.getenv("VCAP_SERVICES");
		if (vcapServices != null) {
			sb.append(
					"<details style=\"background:rgba(0,0,0,0.1); padding:10px; margin-bottom:20px; border-radius:5px;\">");
			sb.append("<summary style=\"cursor:pointer; font-weight:bold;\">VCAP_SERVICES</summary>");
			sb.append(
					"<pre style=\"white-space:pre-wrap; word-wrap:break-word; font-size:0.85em; margin-top:10px; overflow-x: auto;\">")
				.append(HtmlUtils.htmlEscape(prettifyJson(vcapServices)))
				.append("</pre>");
			sb.append("</details>");
		}

		// Environment Variables List
		String[] envVars = { "CF_INSTANCE_ADDR", "CF_INSTANCE_GUID", "CF_INSTANCE_INDEX", "CF_INSTANCE_INTERNAL_IP",
				"CF_INSTANCE_IP", "CF_INSTANCE_PORT", "CF_INSTANCE_PORTS", "CF_STACK", "DATABASE_URL", "HOME",
				"INSTANCE_GUID", "INSTANCE_INDEX", "LANG", "MEMORY_LIMIT", "PATH", "PORT", "PWD",
				"SERVICE_BINDING_ROOT", "TMPDIR", "USER", "VCAP_APP_HOST", "VCAP_APP_PORT", "VCAP_APPLICATION",
				"VCAP_SERVICES", "VCAP_SERVICES_FILE_PATH" };

		sb.append(
				"<details style=\"background:rgba(0,0,0,0.1); padding:10px; margin-bottom:10px; border-radius:5px;\">");
		sb.append("<summary style=\"cursor:pointer; font-weight:bold;\">System Environment Variables List</summary>");
		sb.append(
				"<ul style=\"font-family:monospace; font-size:0.85em; list-style-type:none; padding-left:0; word-wrap:break-word; margin-top: 15px;\">");

		boolean foundAny = false;
		for (String var : envVars) {
			String val = System.getenv(var);
			if (val != null) {
				foundAny = true;
				// Substitute long JSON payload in the plain list view if they are already
				// handled above
				if (var.equals("VCAP_APPLICATION") || var.equals("VCAP_SERVICES")) {
					val = "(See expandable JSON section above)";
				}
				else {
					val = HtmlUtils.htmlEscape(val);
				}
				sb.append(
						"<li style=\"margin-bottom: 8px; border-bottom: 1px dashed rgba(255,255,255,0.2); padding-bottom: 4px;\">")
					.append("<strong>")
					.append(var)
					.append("</strong>: ")
					.append(val)
					.append("</li>");
			}
		}

		if (!foundAny) {
			sb.append("<li><em>No specific Cloud Foundry variables found in environment.</em></li>");
		}

		sb.append("</ul>");
		sb.append("</details>");

		sb.append("</div>");
		return sb.toString();
	}

	private String prettifyJson(String raw) {
		if (raw == null || raw.trim().isEmpty()) {
			return "";
		}
		try {
			Object json = JSON_MAPPER.readValue(raw, Object.class);
			return JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(json);
		}
		catch (Exception e) {
			log.warn("Failed to prettify JSON string: {}", e.getMessage());
			return raw; // Fallback to raw string if it's not valid JSON
		}
	}

}