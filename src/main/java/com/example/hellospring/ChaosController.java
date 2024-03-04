package com.example.hellospring;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ChaosController {

    @Value("${vcap.application.application_name:none}")
	private String applicationName;

	@Value("${vcap.application.application_id:xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx}")
	private String applicationId;	

	@Value("${vcap.application.instance_index:999}")
	private String applicationInstance;	

    static final String GREEN = "#33CC33";
    static final String ORANGE = "#FFA500";
    static final String RED = "#FF0000";

	@Autowired
    private FailReadyUntil failReadyUntil;

	@Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ApplicationAvailability availabilityProvider;

	@GetMapping("/")
    String home(Model model) {
        if (availabilityProvider.getLivenessState() == LivenessState.BROKEN){
            model.addAttribute("backgroundColor", RED);
        } else if(LocalDateTime.now().isBefore(failReadyUntil.get())){
            model.addAttribute("backgroundColor", ORANGE);
        } else {
            model.addAttribute("backgroundColor", GREEN);
        }

        final String content = "<h1>Hello Spring!</h1></br>I am instance <strong>#" 
		+ applicationInstance 
		+ "</strong> serving application <strong>" 
		+ applicationName 
		+ "</strong> with GUID <strong> "
        + applicationId
        + "</strong>!";
        model.addAttribute("content", content);

        return "content";
    }

	@GetMapping("/fail/ready")
    String failReady(Model model) {
        failReadyUntil.set();
        final String content = "<h1>Fail Readiness</h1></br>Readiness for instance <strong>#"
        + applicationInstance
        + "</strong> with GUID <strong> "
        + applicationId
        + "</strong> fails until <strong>"
        + failReadyUntil.get()
        + "</strong>!";

        model.addAttribute("backgroundColor", ORANGE);
        model.addAttribute("content", content);

        return "content";
    }

	@GetMapping("/fail/live")
    String failLive(Model model) {
		AvailabilityChangeEvent.publish(applicationContext, LivenessState.BROKEN);
		final String content =  "<h1>Fail Liveness</h1></br>Liveness for instance <strong>#"
        + applicationInstance
        + "</strong> with GUID <strong> "
        + applicationId
        + "</strong> will fail"
        + "</strong>!";

        model.addAttribute("backgroundColor", RED);
        model.addAttribute("content", content);

        return "content";
    }

	@GetMapping("/kill")
    String kill(Model model) {
		new Thread(() -> {
			try {
                Thread.sleep(3000);
            } catch (InterruptedException excep) {
                System.out.println("InterruptedException aufgetreten");
            }
			SpringApplication.exit(applicationContext, () -> 0);         
        }).start();
            
        final String content = "<h1>Shutting instance <strong>#" 
            + applicationInstance 
            + " down!</h1>";

            model.addAttribute("backgroundColor", RED);
            model.addAttribute("content", content);

            return "content";
        }
}
