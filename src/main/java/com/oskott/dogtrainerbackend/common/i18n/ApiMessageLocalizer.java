package com.oskott.dogtrainerbackend.common.i18n;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ApiMessageLocalizer {

    private static final Pattern NOT_FOUND = Pattern.compile("([A-Za-z]+) not found with id: (.+)");
    private static final Pattern SIZE_BETWEEN = Pattern.compile("size must be between (\\d+) and (\\d+)");
    private static final Pattern GREATER_OR_EQUAL = Pattern.compile("must be greater than or equal to (\\d+)");
    private static final Pattern GREATER_THAN = Pattern.compile("must be greater than (-?\\d+(?:\\.\\d+)?)");
    private static final Pattern LESS_OR_EQUAL = Pattern.compile("must be less than or equal to (-?\\d+(?:\\.\\d+)?)");
    private static final Map<String, String> BOKMAL = Map.ofEntries(
            Map.entry("Validation failed", "Valideringen mislyktes"),
            Map.entry("An unexpected error occurred", "Det oppstod en uventet feil"),
            Map.entry("Authentication is required", "Autentisering kreves"),
            Map.entry("Access is denied", "Tilgang er nektet"),
            Map.entry("You do not have access to this dog", "Du har ikke tilgang til denne hunden"),
            Map.entry("You do not have access to this object", "Du har ikke tilgang til dette objektet"),
            Map.entry("You do not have access to this post", "Du har ikke tilgang til dette innlegget"),
            Map.entry("You do not have access to this comment", "Du har ikke tilgang til denne kommentaren"),
            Map.entry("Only an in-progress session can be completed", "Bare en pågående økt kan fullføres"),
            Map.entry("Only an in-progress session can be cancelled", "Bare en pågående økt kan avbrytes"),
            Map.entry("Exercises can only be modified while the session is in progress", "Øvelser kan bare endres mens økten pågår"),
            Map.entry("Successful repetitions cannot exceed total repetitions", "Antall vellykkede repetisjoner kan ikke overstige totalt antall repetisjoner"),
            Map.entry("One or more exercises referenced by this plan do not exist", "Én eller flere øvelser i denne planen finnes ikke"),
            Map.entry("Only completed training sessions can be shared as a post", "Bare fullførte treningsøkter kan deles som innlegg"),
            Map.entry("This training session has already been posted", "Denne treningsøkten er allerede publisert"),
            Map.entry("Invalid cursor", "Ugyldig markør"),
            Map.entry("An account with this email already exists", "Det finnes allerede en konto med denne e-postadressen"),
            Map.entry("Invalid email or password", "Ugyldig e-postadresse eller passord"),
            Map.entry("Incorrect password", "Feil passord"),
            Map.entry("Invalid refresh token", "Ugyldig oppdateringstoken"),
            Map.entry("Refresh token is expired or revoked", "Oppdateringstokenet er utløpt eller tilbakekalt"),
            Map.entry("This account is no longer active", "Denne kontoen er ikke lenger aktiv"),
            Map.entry("Authenticated user no longer exists", "Den autentiserte brukeren finnes ikke lenger"),
            Map.entry("No authenticated user in the current request context", "Ingen autentisert bruker i gjeldende forespørsel"),
            Map.entry("This provider account is linked to another user", "Denne leverandørkontoen er knyttet til en annen bruker"),
            Map.entry("A different account from this provider is already linked", "En annen konto fra denne leverandøren er allerede knyttet til brukeren"),
            Map.entry("Provider did not supply a verified email address", "Leverandøren oppga ikke en bekreftet e-postadresse"),
            Map.entry("An account with this email already exists. Log in with an existing method, then connect this provider from Profile.", "Det finnes allerede en konto med denne e-postadressen. Logg inn med en eksisterende metode, og koble deretter til leverandøren fra Profil."),
            Map.entry("Enter your name to finish creating your account", "Skriv inn navnet ditt for å fullføre opprettelsen av kontoen"),
            Map.entry("Choose a sign-in method to confirm account deletion", "Velg en innloggingsmetode for å bekrefte sletting av kontoen"),
            Map.entry("Fresh provider authentication is required", "Ny autentisering hos leverandøren kreves"),
            Map.entry("Provider authentication is too old", "Autentiseringen hos leverandøren er for gammel"),
            Map.entry("Provider account is not linked to this user", "Leverandørkontoen er ikke knyttet til denne brukeren"),
            Map.entry("Apple authorization code is required", "Apple-autorisasjonskode kreves"),
            Map.entry("Apple authorization does not match this user", "Apple-autorisasjonen samsvarer ikke med denne brukeren"),
            Map.entry("Apple account deletion is not configured", "Sletting av Apple-konto er ikke konfigurert"),
            Map.entry("Could not revoke Sign in with Apple authorization", "Kunne ikke tilbakekalle autorisasjonen for Logg på med Apple"),
            Map.entry("Provider credential has no subject", "Leverandørlegitimasjonen mangler brukeridentifikator"),
            Map.entry("Provider credential has an invalid email", "Leverandørlegitimasjonen har en ugyldig e-postadresse"),
            Map.entry("Invalid or expired provider credential", "Ugyldig eller utløpt leverandørlegitimasjon"),
            Map.entry("You cannot follow yourself", "Du kan ikke følge deg selv"),
            Map.entry("You are already following this user", "Du følger allerede denne brukeren"),
            Map.entry("You cannot follow this user", "Du kan ikke følge denne brukeren"),
            Map.entry("You cannot block yourself", "Du kan ikke blokkere deg selv"),
            Map.entry("You have already blocked this user", "Du har allerede blokkert denne brukeren"),
            Map.entry("A report must reference a post or a user", "En rapport må vise til et innlegg eller en bruker"),
            Map.entry("Unsupported or corrupt image file", "Bildefilen støttes ikke eller er skadet"),
            Map.entry("must not be blank", "må fylles ut"),
            Map.entry("must not be null", "kan ikke være tom"),
            Map.entry("must be a well-formed email address", "må være en gyldig e-postadresse"),
            Map.entry("must be greater than 0", "må være større enn 0"),
            Map.entry("must be a date in the past or in the present", "må være en dato i fortiden eller i dag"),
            Map.entry("must be an absolute URL starting with http:// or https://", "må være en absolutt URL som starter med http:// eller https://")
    );

    public String localize(String message, HttpServletRequest request) {
        return localize(message, SupportedLocale.resolve(request));
    }

    public String localize(String message) {
        return localize(message, SupportedLocale.current());
    }

    private String localize(String message, String locale) {
        if (!SupportedLocale.BOKMAL.equals(locale) || message == null) {
            return message;
        }
        String exact = BOKMAL.get(message);
        if (exact != null) {
            return exact;
        }
        Matcher notFound = NOT_FOUND.matcher(message);
        if (notFound.matches()) {
            return entityName(notFound.group(1)) + " finnes ikke med ID: " + notFound.group(2);
        }
        Matcher size = SIZE_BETWEEN.matcher(message);
        if (size.matches()) {
            return "størrelsen må være mellom " + size.group(1) + " og " + size.group(2);
        }
        Matcher minimum = GREATER_OR_EQUAL.matcher(message);
        if (minimum.matches()) {
            return "må være større enn eller lik " + minimum.group(1);
        }
        Matcher greaterThan = GREATER_THAN.matcher(message);
        if (greaterThan.matches()) {
            return "må være større enn " + greaterThan.group(1);
        }
        Matcher maximum = LESS_OR_EQUAL.matcher(message);
        if (maximum.matches()) {
            return "må være mindre enn eller lik " + maximum.group(1);
        }
        if (message.startsWith("Unsupported report reason: ")) {
            return "Rapportårsaken støttes ikke: " + message.substring("Unsupported report reason: ".length());
        }
        if (message.startsWith("Unsupported content type")) {
            return "Innholdstypen støttes ikke" + message.substring("Unsupported content type".length());
        }
        if (message.startsWith("Unrecognized media file extension: ")) {
            return "Ukjent filtype for mediefil: "
                    + message.substring("Unrecognized media file extension: ".length());
        }
        if (message.startsWith("File exceeds the maximum allowed size of ")) {
            return "Filen overstiger maksimal tillatt størrelse på "
                    + message.substring("File exceeds the maximum allowed size of ".length());
        }
        if (message.startsWith("Could not read image: ")) {
            return "Kunne ikke lese bildet: " + message.substring("Could not read image: ".length());
        }
        if (message.startsWith("Could not process image: ")) {
            return "Kunne ikke behandle bildet: " + message.substring("Could not process image: ".length());
        }
        if (message.startsWith("dogIds must contain exactly")) {
            return "dogIds må inneholde nøyaktig hundene til gjeldende bruker, én gang hver";
        }
        return message;
    }

    private String entityName(String entity) {
        return switch (entity) {
            case "TrainingCategory" -> "Treningskategorien";
            case "Activity" -> "Aktiviteten";
            case "Exercise" -> "Øvelsen";
            case "TrainingSession" -> "Treningsøkten";
            case "SessionExercise" -> "Øktøvelsen";
            case "TrainingPlan" -> "Treningsplanen";
            case "Dog" -> "Hunden";
            case "User" -> "Brukeren";
            case "Post" -> "Innlegget";
            case "Comment" -> "Kommentaren";
            case "Goal" -> "Målet";
            case "Follow" -> "Følgeforholdet";
            case "Block" -> "Blokkeringen";
            case "Object" -> "Objektet";
            default -> entity;
        };
    }
}
