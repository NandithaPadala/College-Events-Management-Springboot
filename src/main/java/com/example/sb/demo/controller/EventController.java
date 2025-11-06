package com.example.sb.demo.controller;

import com.example.sb.demo.entity.Event;
import com.example.sb.demo.entity.Registration;
import com.example.sb.demo.entity.User;
import com.example.sb.demo.service.EventService;
import com.example.sb.demo.service.RegistrationService;
import com.example.sb.demo.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class EventController {
    private final EventService eventService;
    private final UserService userService;
    private final RegistrationService registrationService;

    private User getCurrentUser(HttpSession session) {
        return userService.getCurrentUser(session)
                .orElseThrow(() -> new RuntimeException("Not authenticated"));
    }

    @GetMapping("/")
    public String home(Model model, HttpSession session) {
        boolean isLogin = false;
        var userOpt = userService.getCurrentUser(session);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            isLogin = true;
            model.addAttribute("user", user);
            model.addAttribute("isAdmin", userService.isAdmin(user));
        }

        model.addAttribute("isLogin", isLogin);
        model.addAttribute("upcomingEvents", eventService.getUpcomingEvents());
        return "home";
    }

    @GetMapping("/events")
    public String listEvents(Model model, HttpSession session) {
        
        User user = getCurrentUser(session);
       
	    model.addAttribute("user", user);
	    model.addAttribute("isAdmin", userService.isAdmin(user));
	    model.addAttribute("events", eventService.getAllEvents());
	    
	    
	    return "events/list";
    }

    @GetMapping("/events/new")
    public String newEventForm(Model model, HttpSession session) {
        getCurrentUser(session); // Ensure user is authenticated
        model.addAttribute("event", new Event());
        return "events/form";
    }

    @PostMapping("/events")
    public String createEvent(@RequestParam String eventDate,
                            @RequestParam String eventTime,
                            @RequestParam String title,
                            @RequestParam String description,
                            @RequestParam String venue,
                            @RequestParam(required = false) Integer maxParticipants,
                            @RequestParam(required = false) MultipartFile imageFile,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        try {
            // Validate date and time
            LocalDateTime eventDateTime = combineDateTime(eventDate, eventTime);
            if (eventDateTime.isBefore(LocalDateTime.now())) {
                throw new RuntimeException("Event date must be in the future");
            }
            Event event = new Event();
            event.setTitle(title);
            event.setDescription(description);
            event.setVenue(venue);
            event.setMaxParticipants(maxParticipants);
            event.setEventDate(eventDateTime);

            // Handle image upload
            if (imageFile != null && !imageFile.isEmpty()) {
                String imageUrl = handleImageUpload(imageFile);
                event.setImageUrl(imageUrl);
            }

            User user = getCurrentUser(session);
            eventService.createEvent(event, user);
            redirectAttributes.addFlashAttribute("successMessage", "Event created successfully!");
            return "redirect:/events";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/events/new";
        }
    }

    private LocalDateTime combineDateTime(String date, String timeStr) {
        if (date == null) {
            throw new RuntimeException("Event date is required");
        }
        if (timeStr == null || timeStr.trim().isEmpty()) {
            throw new RuntimeException("Event time is required");
        }
        try {
            LocalTime time = LocalTime.parse(timeStr);
            LocalDateTime dateTime = LocalDateTime.parse(date + "T00:00:00");
            return LocalDateTime.of(dateTime.toLocalDate(), time);
        } catch (Exception e) {
            throw new RuntimeException("Invalid date or time format. Please use YYYY-MM-DD for date and HH:mm for time.");
        }
    }

    private String handleImageUpload(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return null;
        }

        // Create the uploads directory if it doesn't exist
        String uploadDir = "uploads/events";
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Generate a unique filename
        String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(fileName);

        // Save the file
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        return "/" + uploadDir + "/" + fileName;
    }

    @GetMapping("/events/{id}/edit")
    public String editEventForm(@PathVariable Long id,
                              Model model,
                              HttpSession session) {
        User user = getCurrentUser(session);
        Event event = eventService.getEventById(id);
        
        if (!user.getRole().equals("ADMIN") && !event.getCreatedBy().getId().equals(user.getId())) {
            throw new RuntimeException("Not authorized to edit this event");
        }
        
        model.addAttribute("event", event);
        return "events/form";
    }

    @PostMapping("/events/{id}")
    public String updateEvent(@PathVariable Long id,
                            @ModelAttribute Event event,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        try {
            User user = getCurrentUser(session);
            eventService.updateEvent(id, event, user);
            redirectAttributes.addFlashAttribute("successMessage", "Event updated successfully!");
            return "redirect:/events";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/events/" + id + "/edit";
        }
    }

    
    @PostMapping("/events/{id}/delete")
    public String deleteEvent(@PathVariable Long id,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        try {
            User currentUser = getCurrentUser(session);
            Event event = eventService.getEventById(id);

            // ✅ Allow only creator or admin
            if (!currentUser.isAdmin() && !event.getCreatedBy().getId().equals(currentUser.getId())) {
                redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to delete this event.");
                return "redirect:/events";
            }

            eventService.deleteEvent(id, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "Event deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting event: " + e.getMessage());
        }

        return "redirect:/events";
    }

    @GetMapping("/events/{id}/register")
    public String showRegistrationForm(@PathVariable Long id,
                                       Model model,
                                       HttpSession session,
                                       RedirectAttributes redirectAttributes) {
    	 try {
    	        User user = getCurrentUser(session);
    	        Event event = eventService.getEventById(id);

    	        if (registrationService.isAlreadyRegistered(event, user)) {
    	            redirectAttributes.addFlashAttribute("errorMessage", "You are already registered for this event");
    	            return "redirect:/events/" + id;
    	        }

    	        registrationService.registerForEvent(event, user);
    	        redirectAttributes.addFlashAttribute("successMessage", "Successfully registered! Status: PENDING");
    	    } catch (Exception e) {
    	        redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
    	    }
    	    return "redirect:/events/" + id; // redirect back to event details
    }

    
    @PostMapping("/events/{id}/register")
    public String registerForEvent(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        try {
            User user = getCurrentUser(session);
            Event event = eventService.getEventById(id);

            // Prevent creator from registering
            if (event.getCreatedBy().getId().equals(user.getId())) {
                redirectAttributes.addFlashAttribute("errorMessage", "Organizers cannot register for their own events.");
                return "redirect:/events/" + id;
            }

            registrationService.registerForEvent(event, user);
            redirectAttributes.addFlashAttribute("successMessage", "Successfully registered! Status: PENDING");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/events/" + id;
    }


    @GetMapping("/events/{id}")
    public String viewEventDetails(@PathVariable Long id, HttpSession session, Model model) {
        Event event = eventService.getEventById(id);
        User user = getCurrentUser(session);
        boolean isAdmin = userService.isAdmin(user);

        boolean isCreator = false;
        boolean isRegistered = false;
        String registrationStatus = null;
        Registration registration = null;
        Long registrationId = null;

        if (user != null && event.getCreatedBy().getId().equals(user.getId())) {
        	isCreator = true;
        } else if (user != null) {
            isCreator = event.getCreatedBy().getId().equals(user.getId());
            registration = registrationService
                .getEventRegistrations(event)
                .stream()
                .filter(r -> r.getUser().getId().equals(user.getId()))
                .findFirst()
                .orElse(null);

            if (registration != null) {
                isRegistered = true;
                registrationStatus = registration.getStatus(); // PENDING / APPROVED / REJECTED 
                registrationId = registration.getId(); 
            }
        }
        

        model.addAttribute("event", event);
        model.addAttribute("isCreator", isCreator);
        model.addAttribute("isRegistered", isRegistered);
        model.addAttribute("registrationStatus", registrationStatus);
        model.addAttribute("registrationId", registrationId);
        model.addAttribute("registration", registration);
        model.addAttribute("isAdmin", isAdmin);

        return "events/details";
    }
    
    @PostMapping("/registrations/{registrationId}/update-status")
    public String updateRegistrationStatus(@PathVariable Long registrationId,
                                           @RequestParam("status") String status,
                                           HttpSession session,
                                           RedirectAttributes redirectAttributes) {
        try {
            User currentUser = getCurrentUser(session);

            // Fetch registration
            Registration registration = registrationService.getAllRegistrations()
                    .stream()
                    .filter(r -> r.getId().equals(registrationId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Registration not found"));

            Event event = registration.getEvent();

            // Authorization: Only creator can update
            if (!event.getCreatedBy().getId().equals(currentUser.getId())) {
                redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to update registration status");
                return "redirect:/events/" + event.getId() + "/registrations";
            }

            registrationService.updateRegistrationStatus(registrationId, status, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "Registration status updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        // ✅ Redirect back to that event's registrations page
        return "redirect:/events/" + registrationService
                .getAllRegistrations()
                .stream()
                .filter(r -> r.getId().equals(registrationId))
                .findFirst()
                .map(r -> r.getEvent().getId())
                .orElse(0L) + "/registrations";
    }

    
    @GetMapping("/events/{id}/registrations")
    public String viewEventRegistrations(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        try {
            User currentUser = getCurrentUser(session);
            Event event = eventService.getEventById(id);

            // Ensure only event creator can view registrations
            if (!event.getCreatedBy().getId().equals(currentUser.getId()) && !userService.isAdmin(currentUser)) {
                redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to view registrations for this event");
                return "redirect:/events/" + id;
            }

            List<Registration> registrations = registrationService.getEventRegistrations(event);
            model.addAttribute("event", event);
            model.addAttribute("registrations", registrations);

            return "events/registrations"; // Thymeleaf page to show registrations
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/events";
        }
    }
    
    @GetMapping("/events/{id}/registrations/pending")
    public String viewPendingRegistrations(@PathVariable Long id,
                                           HttpSession session,
                                           Model model,
                                           RedirectAttributes redirectAttributes) {
        try {
            User currentUser = getCurrentUser(session);
            Event event = eventService.getEventById(id);

            // Only event creator can view
            if (!event.getCreatedBy().getId().equals(currentUser.getId())) {
                redirectAttributes.addFlashAttribute("errorMessage", "You are not authorized to view this page.");
                return "redirect:/events/" + id;
            }

            // ✅ Get pending registrations only
            List<Registration> pendingRegistrations = registrationService.getPendingRegistrations(event);

            model.addAttribute("event", event);
            model.addAttribute("registrations", pendingRegistrations);
            model.addAttribute("filterType", "pending"); // optional, for UI

            return "events/registrations"; // reuse same Thymeleaf page
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/events";
        }
    }

    @PostMapping("/registrations/{id}/cancel")
    public String cancelRegistration(@PathVariable Long id,
                                     HttpSession session,
                                     RedirectAttributes redirectAttributes) {
        try {
            User currentUser = (User) session.getAttribute("user");
            registrationService.cancelRegistration(id, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "Registration cancelled successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/events"; 
    }
    





    
    
}