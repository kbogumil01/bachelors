package com.dispatcher.server.dispatcherServer.controller;

import com.dispatcher.server.dispatcherServer.entity.UserEntity;
import com.dispatcher.server.dispatcherServer.model.User;
import com.dispatcher.server.dispatcherServer.services.AuthService;
import com.dispatcher.server.dispatcherServer.services.EndpointService;
import com.dispatcher.server.dispatcherServer.services.TokenService;
import com.dispatcher.server.dispatcherServer.services.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hibernate.tool.schema.internal.exec.ScriptTargetOutputToFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;

@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
@RestController
@RequestMapping("/api/v1")
public class UserController {
    @Autowired
    private UserService userService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private AuthService authService;

    @Autowired
    private EndpointService endpointService;

    public UserController(UserService userService,
                          TokenService tokenService,
                          AuthService authService,
                          EndpointService endpointService)
            throws NoSuchAlgorithmException {
        this.userService = userService;
        this.tokenService = tokenService;
        this.authService = authService;
        this.endpointService = endpointService;
    }

    @PostMapping("/register") //rejestracja
    public ResponseEntity<User> createUser(HttpServletRequest request, @RequestBody User user, @RequestParam long id) {
        boolean permission = false;

        if (authService.authUser(request, "admin", id)) {
            permission = true;
        }

        user = userService.createUser(user, permission);
        if (user == null) {
            return ResponseEntity.status(409).body(null); //jesli login jest już zajęty
        } else {
            return ResponseEntity.ok(user);
        }
    }

    @PostMapping("/login") //logowanie
    public ResponseEntity<User> authentication(@RequestBody User user,
                                               HttpServletResponse response) {
        user = userService.authentication(user);
        if (user != null) {
            Cookie cookie = tokenService.getCookie(user);
            response.addCookie(cookie);
            user.setPassword("***");
            return ResponseEntity.ok(user);
        } else {
            return ResponseEntity.status(401).body(null);
        }
    }

    @GetMapping("/logout")
    public boolean logout(HttpServletRequest request) {
        if (authService.logoutUser(request)) {
            return true;
        } else {
            return false;
        }
    }

    @PostMapping("/practitioner")
    public ResponseEntity<Long> registerNewFhirPractitioner(@RequestBody Object object,
                                                            HttpServletRequest request,
                                                            @RequestParam long id,
                                                            @RequestParam long res_id)
            throws JsonProcessingException {
        if (authService.authUser(request, "admin", id)) {
            JsonNode responseJson = endpointService.contactToFhir(object, "http://localhost:8080/fhir/Practitioner", "POST");
            long idFromResponse = responseJson.path("id").asLong();
            return ResponseEntity.ok(idFromResponse);
        } else if (authService.authUser(request, "med", id)) {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode patientToFind = objectMapper.valueToTree(object);
            String PESELToFind = patientToFind.findValue("identifier").findValue("value").asText();
            JsonNode findByPESEL = endpointService.contactToFhir(null, "http://localhost:8080/fhir/Practitioner?identifier=" + PESELToFind, "GET");
            if (!findByPESEL.findValue("total").asText().equals("0")) { //jest juz taki med w bazie dodany przez admina
                if (findByPESEL.findValue("entry").findValue("identifier").findValue("value").asText().equals(PESELToFind)) { //pesel znaleziony jest taki sam jak pesel w pacjencie
                    long idOfPractitioner = findByPESEL.findValue("entry").findValue("id").asLong();
                    if (userService.setResourceToUser(id, res_id, idOfPractitioner)) { //po prostu dodaj id pacjenta do usera
                        return ResponseEntity.ok(idOfPractitioner);
                    } else {
                        return ResponseEntity.status(HttpStatus.CONFLICT).build();
                    }
                } else {
                    return ResponseEntity.status(HttpStatus.CONFLICT).build();
                }
            } else { //takiego meda nie ma
                JsonNode responseJson = endpointService.contactToFhir(object, "http://localhost:8080/fhir/Practitioner", "POST"); //stworz nowego practitionera i dodaj jego id do usera
                long idFromResponse = responseJson.path("id").asLong();
                if (userService.setResourceToUser(id, res_id, idFromResponse)) {
                    return ResponseEntity.ok(idFromResponse);
                } else {
                    return ResponseEntity.status(HttpStatus.CONFLICT).build();
                }
            }
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/patient") //dodanie pacjenta i powiązanie go z userem
    public ResponseEntity<Long> registerNewFhirPatient(@RequestBody Object object,
                                                       HttpServletRequest request,
                                                       @RequestParam long id,
                                                       @RequestParam long res_id)
            throws JsonProcessingException {

        if (authService.authUser(request, "patient", id)) { //jesli autoryzacje dostanie pacjent
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode patientToFind = objectMapper.valueToTree(object);
            String PESELToFind = patientToFind.findValue("identifier").findValue("value").asText();
            JsonNode findByPESEL = endpointService.contactToFhir(null, "http://localhost:8080/fhir/Patient?identifier=" + PESELToFind, "GET");

            if (!findByPESEL.findValue("total").asText().equals("0")) { //znaleziono pacjenta o takim peselu juz w bazie pacjentow
                if (findByPESEL.findValue("entry").findValue("identifier").findValue("value").asText().equals(PESELToFind)) { //pesel znaleziony jest taki sam jak pesel w pacjencie
                    long idOfPatient = findByPESEL.findValue("entry").findValue("id").asLong();
                    if (userService.setResourceToUser(id, res_id, idOfPatient)) { //po prostu dodaj id pacjenta do usera
                        return ResponseEntity.ok(idOfPatient);
                    } else {
                        return ResponseEntity.status(HttpStatus.CONFLICT).build();
                    }
                } else { //pesele znalezione i pacjenta sie nie zgadzają
                    return ResponseEntity.status(HttpStatus.CONFLICT).build();
                }
            } else { // nie znaleziono
                JsonNode responseJson = endpointService.contactToFhir(object, "http://localhost:8080/fhir/Patient", "POST"); //stworz nowego pacjenta i dodaj jego id do usera
                long idFromResponse = responseJson.path("id").asLong();
                if (userService.setResourceToUser(id, res_id, idFromResponse)) {
                    return ResponseEntity.ok(idFromResponse);
                } else {
                    return ResponseEntity.status(HttpStatus.CONFLICT).build();
                }
            }
        } else if (authService.authUser(request, "admin", id) || authService.authUser(request, "med", id)) { //jesli autoryzacje dostanie med lub admin
            JsonNode responseJson = endpointService.contactToFhir(object, "http://localhost:8080/fhir/Patient", "POST");
            long idFromResponse = responseJson.path("id").asLong();
            return ResponseEntity.ok(idFromResponse);
        } else { //jesli nie dostanie autoryzacji
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

    }

    @GetMapping("/patient")
    public ResponseEntity<JsonNode> getPatients(HttpServletRequest request, @RequestParam int count, @RequestParam long id) throws JsonProcessingException {
        if (authService.authUser(request, "admin", id) || authService.authUser(request, "med", id)) {
            JsonNode response = endpointService.contactToFhir(null, "http://localhost:8080/fhir/Patient?_count=" + count, "GET");
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @GetMapping("/patient/getById") //zweryfikować resID jesli role==patient !!!!! (done)
    public ResponseEntity<JsonNode> getPatientById(HttpServletRequest request, @RequestParam long userID, @RequestParam long patientID) throws JsonProcessingException {
        if (authService.authUser(request, "admin", userID) || authService.authUser(request, "med", userID) || authService.authUser(request, "patient", userID)) {
            boolean enable = false;
            if (userService.checkRole(userID).equals("patient")) {
                if (userService.authorizeID(userID, patientID)) {
                    enable = true;
                }
            } else {
                enable = true;
            }
            if (enable) {
                JsonNode response = endpointService.contactToFhir(null, "http://localhost:8080/fhir/Patient/" + patientID, "GET");
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @GetMapping("/practitioner/getById") //zweryfikować resID jesli role==patient !!!!! (done)
    public ResponseEntity<JsonNode> getPractitionerById(HttpServletRequest request, @RequestParam long userID, @RequestParam long practitionerID) throws JsonProcessingException {
        if (authService.authUser(request, "admin", userID) || authService.authUser(request, "med", userID)) {
            boolean enable = false;
            if (userService.checkRole(userID).equals("med")) {
                if (userService.authorizeID(userID, practitionerID)) {
                    enable = true;
                }
            } else {
                enable = true;
            }
            if (enable) {
                JsonNode response = endpointService.contactToFhir(null, "http://localhost:8080/fhir/Practitioner/" + practitionerID, "GET");
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @GetMapping("/practitioner")
    public ResponseEntity<JsonNode> getPractitioners(HttpServletRequest request, @RequestParam int count, @RequestParam long id) throws JsonProcessingException {
        if (authService.authUser(request, "admin", id)) {
            JsonNode response = endpointService.contactToFhir(null, "http://localhost:8080/fhir/Practitioner?_count=" + count, "GET");
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @GetMapping("/resource/url")
    public ResponseEntity<JsonNode> getResources(HttpServletRequest request, @RequestParam long id, @RequestParam String url) throws JsonProcessingException {
        //System.out.println(url);
        if (authService.authUser(request, "admin", id) || authService.authUser(request, "med", id)) {
            byte[] BytesUrl = Base64.getDecoder().decode(url);
            String decodedUrl = new String(BytesUrl);
            //System.out.println("\n"+url+"\n");
            JsonNode response = endpointService.contactToFhir(null, decodedUrl, "GET");
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @GetMapping("/patient/search")
    public ResponseEntity<JsonNode> searchPatientByFamily(HttpServletRequest request, @RequestParam long id, @RequestParam String family) throws JsonProcessingException {
        if (authService.authUser(request, "admin", id) || authService.authUser(request, "med", id)) {
            JsonNode response = endpointService.contactToFhir(null, "http://localhost:8080/fhir/Patient/_search?family=" + family, "GET");
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @GetMapping("/practitioner/search")
    public ResponseEntity<JsonNode> searchPractitionerByFamily(HttpServletRequest request, @RequestParam long id, @RequestParam String family) throws JsonProcessingException {
        if (authService.authUser(request, "admin", id)) {
            JsonNode response = endpointService.contactToFhir(null, "http://localhost:8080/fhir/Practitioner/_search?family=" + family, "GET");
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PutMapping("patient/edit")
    public ResponseEntity<JsonNode> editPatient(HttpServletRequest request, @RequestBody Object object, @RequestParam long userID, @RequestParam long patientID) throws JsonProcessingException {
        if (authService.authUser(request, "admin", userID) || authService.authUser(request, "patient", userID)) {
            boolean enable = false;
            if (userService.checkRole(userID).equals("patient")) {
                if (userService.authorizeID(userID, patientID)) {
                    enable = true;
                }
            } else {
                enable = true;
            }
            if (enable) {
                JsonNode response = endpointService.contactToFhir(object, "http://localhost:8080/fhir/Patient/" + patientID, "PUT");
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PutMapping("practitioner/edit")
    public ResponseEntity<JsonNode> editPractitioner(HttpServletRequest request, @RequestBody Object object, @RequestParam long userID, @RequestParam long practitionerID) throws JsonProcessingException {
        if (authService.authUser(request, "admin", userID) || authService.authUser(request, "med", userID)) {
            boolean enable = false;
            if (userService.checkRole(userID).equals("med")) {
                if (userService.authorizeID(userID, practitionerID)) {
                    enable = true;
                }
            } else {
                enable = true;
            }
            if (enable) {
                JsonNode response = endpointService.contactToFhir(object, "http://localhost:8080/fhir/Practitioner/" + practitionerID, "PUT");
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers(HttpServletRequest request, @RequestParam long userID) {
        if (authService.authUser(request, "admin", userID)) {
            return ResponseEntity.ok(userService.getAllUsers());
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @GetMapping("/users/search")
    public ResponseEntity<List<User>> searchUser(HttpServletRequest request, @RequestParam long userID, @RequestParam String searchQuery) {
        if (authService.authUser(request, "admin", userID)) {
            return ResponseEntity.ok(userService.searchUsers(searchQuery));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @DeleteMapping("/users/delete")
    public ResponseEntity<Long> deleteUser(HttpServletRequest request, @RequestParam long userID, @RequestParam long userToDelete, @RequestParam String role)
            throws JsonProcessingException {
        if (authService.authUser(request, "admin", userID)) {
            long resID = userService.checkResource(userToDelete);
            if (resID == 0) { //user nie ma resource
                userService.deleteUser(userToDelete);
                return ResponseEntity.ok(userToDelete);
            } else {
                if (role.equals("patient")) {
                    endpointService.contactToFhir(null, "http://localhost:8080/fhir/Patient/" + resID, "DELETE");
                    userService.deleteUser(userToDelete);
                    return ResponseEntity.ok(userToDelete);
                } else if (role.equals("med")) {
                    endpointService.contactToFhir(null, "http://localhost:8080/fhir/Practitioner/" + resID, "DELETE");
                    userService.deleteUser(userToDelete);
                    return ResponseEntity.ok(userToDelete);
                } else {
                    return ResponseEntity.status(502).build();
                }
            }
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

    }

    @DeleteMapping("resource/delete")
    public ResponseEntity<Long> deleteResource(HttpServletRequest request, @RequestParam long userID, @RequestParam long resourceToDelete, @RequestParam String role)
            throws JsonProcessingException {
        if(authService.authUser(request,"admin",userID)){
            long userThatHasThisResource=userService.findUserByHisResource(resourceToDelete);
            if( userThatHasThisResource != 0 ){ //jakis user ma ten resource
                userService.setNewResID(userThatHasThisResource,0); //dac mu 0 zeby nie mial zadnego resource nie działa ta metoda
            }
            //usuwanie resource:
            if( role.equals("med") ){
                endpointService.contactToFhir(null, "http://localhost:8080/fhir/Practitioner/" + resourceToDelete, "DELETE");
                return ResponseEntity.ok(resourceToDelete);
            }else if( role.equals("patient") ){
                endpointService.contactToFhir(null, "http://localhost:8080/fhir/Patient/" + resourceToDelete, "DELETE");
                return ResponseEntity.ok(resourceToDelete);
            }else{
                return ResponseEntity.status(502).build();
            }
        }else{
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @GetMapping("resource/assign")
    public ResponseEntity<Long> assignResourceToUser(HttpServletRequest request, @RequestParam long userID, @RequestParam long usr, @RequestParam long res)
        throws JsonProcessingException{
        if(authService.authUser(request,"admin",userID)){
            long resourceOfThisUser=userService.checkResource(usr);
            if(resourceOfThisUser==0){ //user nie ma reource, ale czy resource nie ma usera?
                long userThatHasThisResource = userService.findUserByHisResource(res);
                if(userThatHasThisResource==0){ //resource nie ma usera
                    userService.setNewResID(usr,res);
                    return ResponseEntity.ok(usr);
                }
                else{
                    return ResponseEntity.status(HttpStatus.CONFLICT).build();
                }
            }else{
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }
        }else{
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("condition")
    public ResponseEntity<JsonNode> addCondition (HttpServletRequest request, @RequestBody Object object, @RequestParam long userID, @RequestParam long resID)
        throws JsonProcessingException{
        if(authService.authUser(request,"med",userID)){
            long userResource=userService.checkResource(userID);
            if(userResource==resID) {
                return ResponseEntity.ok(endpointService.contactToFhir(object, "http://localhost:8080/fhir/Condition", "POST"));
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    @PostMapping("observation")
    public ResponseEntity<JsonNode> addObservation (HttpServletRequest request, @RequestBody Object object, @RequestParam long userID, @RequestParam long resID)
            throws JsonProcessingException{
        if(authService.authUser(request,"med",userID)){
            long userResource=userService.checkResource(userID);
            if(userResource==resID) {
                return ResponseEntity.ok(endpointService.contactToFhir(object, "http://localhost:8080/fhir/Observation", "POST"));
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

//    @PostMapping("procedure")
//    public ResponseEntity<JsonNode> addProcedure (HttpServletRequest request, @RequestBody Object object, @RequestParam long userID, @RequestParam long resID)
//            throws JsonProcessingException{
//        if(authService.authUser(request,"med",userID)){
//            long userResource=userService.checkResource(userID);
//            if(userResource==resID) {
//                return ResponseEntity.ok(endpointService.contactToFhir(object, "http://localhost:8080/fhir/Procedure", "POST"));
//            }
//        }
//        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
//    }

    @PostMapping("medicationRequest")
    public ResponseEntity<JsonNode> addMedicationRequest (HttpServletRequest request, @RequestBody Object object, @RequestParam long userID, @RequestParam long resID)
            throws JsonProcessingException{
        if(authService.authUser(request,"med",userID)){
            long userResource=userService.checkResource(userID);
            if(userResource==resID) {
                System.out.println(object.toString());
                return ResponseEntity.ok(endpointService.contactToFhir(object, "http://localhost:8080/fhir/MedicationRequest", "POST"));
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @GetMapping("condition")
    public ResponseEntity<JsonNode> getAllPatientConditions (HttpServletRequest request, @RequestParam long userID, @RequestParam long patientID)
        throws JsonProcessingException{
        if(authService.authUser(request,"med",userID) || authService.authUser(request,"patient",userID)){
            String role = userService.checkRole(userID);
            if(role=="patient"){ //chcemy sie upewnic ze pacjent nie chce pobrac danych innego pacjenta
                long realResource=userService.checkResource(userID);
                if(realResource==patientID){
                    return ResponseEntity.ok(endpointService.contactToFhir(null, "http://localhost:8080/fhir/Condition?subject=Patient/"+patientID, "GET"));
                }
            }else{
                return ResponseEntity.ok(endpointService.contactToFhir(null, "http://localhost:8080/fhir/Condition?subject=Patient/"+patientID, "GET"));
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }


    @GetMapping("condition/patient")
    public ResponseEntity<JsonNode> getPatientCondition (HttpServletRequest request, @RequestParam long userID, @RequestParam long conditionID)
        throws JsonProcessingException{
        if(authService.authUser(request,"med",userID)||authService.authUser(request,"patient",userID)){
            String role=userService.checkRole(userID);
            if(role=="patient"){
                long realResource=userService.checkResource(userID);

                JsonNode resp = endpointService.contactToFhir(null, "http://localhost:8080/fhir/Condition/"+conditionID, "GET");
                if(resp.findValue("subject").findValue("reference").asText().equals("Patient/"+realResource)){
                    return ResponseEntity.ok(resp);
                }

            }else{
                return ResponseEntity.ok(endpointService.contactToFhir(null, "http://localhost:8080/fhir/Condition/"+conditionID, "GET"));
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @GetMapping("observation")
    public ResponseEntity<JsonNode> getAllPatientObseravtions (HttpServletRequest request, @RequestParam long userID, @RequestParam long conditionID)
            throws JsonProcessingException{
        if(authService.authUser(request,"med",userID)||authService.authUser(request,"patient",userID)){
            String role=userService.checkRole(userID);
            if(role=="patient"){
                long patientID=userService.checkResource(userID);
                //trzeba sprawdzic czy ta condition jest faktycznie powiązana z pacjentem
                JsonNode ConditionToVerify = endpointService.contactToFhir(null,"http://localhost:8080/fhir/Condition/"+conditionID,"GET");
                if(ConditionToVerify.findValue("subject").findValue("reference").equals("Patient/"+patientID)){ //zgadza się, należy
                    return ResponseEntity.ok(endpointService.contactToFhir(null, "http://localhost:8080/fhir/Observation?focus="+conditionID, "GET"));
                }
            }else{
                return ResponseEntity.ok(endpointService.contactToFhir(null, "http://localhost:8080/fhir/Observation?focus="+conditionID, "GET"));
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @GetMapping("observation/patient")
    public ResponseEntity<JsonNode> getPatientObseravtion (HttpServletRequest request, @RequestParam long userID, @RequestParam long obsID)
            throws JsonProcessingException{
        if(authService.authUser(request,"med",userID)||authService.authUser(request,"patient",userID)){
            String role=userService.checkRole(userID);
            if(role=="patient"){
                long patientID=userService.checkResource(userID);
                //trzeba sprawdzic czy ta observation jest faktycznie powiązana z pacjentem
                JsonNode ObservationToVerify = endpointService.contactToFhir(null,"http://localhost:8080/fhir/Observation/"+obsID,"GET");
                if(ObservationToVerify.findValue("subject").findValue("reference").equals("Patient/"+patientID)){ //zgadza się, należy
                    return ResponseEntity.ok(ObservationToVerify);
                }
            }else{
                return ResponseEntity.ok(endpointService.contactToFhir(null, "http://localhost:8080/fhir/Observation/"+obsID, "GET"));
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    ////////23.09.2023////// nie testowane endpointy:
    /// endpointy do geta wszystkich i konkretnych procedures i recept:

//    @GetMapping("procedure")
//    public ResponseEntity<JsonNode> getAllPatientProcedures (HttpServletRequest request, @RequestParam long userID, @RequestParam long conditionID)
//            throws JsonProcessingException{
//        if(authService.authUser(request,"med",userID)||authService.authUser(request,"patient",userID)){
//            String role=userService.checkRole(userID);
//            if(role=="patient"){
//                long patientID=userService.checkResource(userID);
//                //trzeba sprawdzic czy ta condition jest faktycznie powiązana z pacjentem
//                JsonNode ConditionToVerify = endpointService.contactToFhir(null,"http://localhost:8080/fhir/Condition/"+conditionID,"GET");
//                if(ConditionToVerify.findValue("subject").findValue("reference").equals("Patient/"+patientID)){ //zgadza się, należy
//                    return ResponseEntity.ok(endpointService.contactToFhir(null, "http://localhost:8080/fhir/Procedure?reason-reference=Condition/"+conditionID, "GET"));
//                }
//            }else{
//                return ResponseEntity.ok(endpointService.contactToFhir(null, "http://localhost:8080/fhir/Procedure?reason-reference=Condition/"+conditionID, "GET"));
//            }
//        }
//        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
//    }

    @GetMapping("medicationRequest")
    public ResponseEntity<JsonNode> getAllPatientMedicationRequests (HttpServletRequest request, @RequestParam long userID, @RequestParam long patientID)
            throws JsonProcessingException{
        if(authService.authUser(request,"med",userID)||authService.authUser(request,"patient",userID)){
            String role=userService.checkRole(userID);
            if(role=="patient"){
                if(userService.authorizeID(userID,patientID)){ //zgadza się, należy
                    return ResponseEntity.ok(endpointService.contactToFhir(null, "http://localhost:8080/fhir/MedicationRequest?subject="+patientID, "GET"));
                }
            }else{
                return ResponseEntity.ok(endpointService.contactToFhir(null, "http://localhost:8080/fhir/MedicationRequest?subject="+patientID, "GET"));
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

//    @GetMapping("procedure/patient")
//    public ResponseEntity<JsonNode> getPatientProcedure (HttpServletRequest request, @RequestParam long userID, @RequestParam long procedureID)
//            throws JsonProcessingException{
//        if(authService.authUser(request,"med",userID)||authService.authUser(request,"patient",userID)){
//            String role=userService.checkRole(userID);
//            if(role=="patient"){
//                long patientID=userService.checkResource(userID);
//                //trzeba sprawdzic czy ta observation jest faktycznie powiązana z pacjentem
//                JsonNode ProcedureToVerify = endpointService.contactToFhir(null,"http://localhost:8080/fhir/Procedure/"+procedureID,"GET");
//                if(ProcedureToVerify.findValue("subject").findValue("reference").equals("Patient/"+patientID)){ //zgadza się, należy
//                    return ResponseEntity.ok(ProcedureToVerify);
//                }
//            }else{
//                return ResponseEntity.ok(endpointService.contactToFhir(null, "http://localhost:8080/fhir/Procedure/"+procedureID, "GET"));
//            }
//        }
//        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
//    }
    @GetMapping("medicationRequest/patient")
    public ResponseEntity<JsonNode> getMedicationRequest (HttpServletRequest request, @RequestParam long userID, @RequestParam long requestID)
            throws JsonProcessingException{
        if(authService.authUser(request,"med",userID)||authService.authUser(request,"patient",userID)){
            String role=userService.checkRole(userID);
            if(role=="patient"){
                long patientID=userService.checkResource(userID);
                //trzeba sprawdzic czy ta observation jest faktycznie powiązana z pacjentem
                JsonNode RequestToVerify = endpointService.contactToFhir(null,"http://localhost:8080/fhir/MedicationRequest/"+requestID,"GET");
                if(RequestToVerify.findValue("subject").findValue("reference").equals("Patient/"+patientID)){ //zgadza się, należy
                    return ResponseEntity.ok(RequestToVerify);
                }
            }else{
                return ResponseEntity.ok(endpointService.contactToFhir(null, "http://localhost:8080/fhir/MedicationRequest/"+requestID, "GET"));
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }


}
