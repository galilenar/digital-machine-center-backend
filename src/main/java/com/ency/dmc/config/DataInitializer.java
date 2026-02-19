package com.ency.dmc.config;

import com.ency.dmc.model.*;
import com.ency.dmc.repository.ProductRepository;
import com.ency.dmc.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) return;

        log.info("Initializing demo data...");

        List<User> users = createUsers();
        List<Product> products = loadAndCreateProducts(users);

        log.info("Demo data initialized: {} users, {} products",
                userRepository.count(), productRepository.count());
    }

    private List<User> createUsers() {
        List<User> users = new ArrayList<>();

        users.add(userRepository.save(User.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin"))
                .email("admin@ency.com")
                .fullName("System Administrator")
                .company("ENCY")
                .role(UserRole.ADMIN)
                .build()));

        users.add(userRepository.save(User.builder()
                .username("vendor")
                .password(passwordEncoder.encode("vendor"))
                .email("vendor@ency.com")
                .fullName("Vendor Moderator")
                .company("ENCY")
                .role(UserRole.VENDOR)
                .build()));

        users.add(userRepository.save(User.builder()
                .username("dealer")
                .password(passwordEncoder.encode("dealer"))
                .email("dealer@partner.com")
                .fullName("Partner Dealer")
                .company("CNC Solutions Ltd")
                .role(UserRole.DEALER)
                .build()));

        users.add(userRepository.save(User.builder()
                .username("user")
                .password(passwordEncoder.encode("user"))
                .email("user@example.com")
                .fullName("John Smith")
                .company("Manufacturing Co")
                .role(UserRole.USER)
                .build()));

        String[][] extraDealers = {
                {"dealer_asia", "Asia Pacific Dealer", "dealer_asia@partner.com", "CNC Asia Pacific"},
                {"dealer_eu", "European Dealer", "dealer_eu@partner.com", "EuroTech Solutions"},
                {"dealer_us", "US Dealer", "dealer_us@partner.com", "American CNC Supply"},
        };
        for (String[] d : extraDealers) {
            users.add(userRepository.save(User.builder()
                    .username(d[0]).password(passwordEncoder.encode("dealer"))
                    .email(d[2]).fullName(d[1]).company(d[3])
                    .role(UserRole.DEALER).build()));
        }

        String[][] extraVendors = {
                {"vendor_robots", "Robotics Vendor", "vendor_robots@ency.com", "ENCY Robotics"},
                {"vendor_post", "Post Processor Vendor", "vendor_post@ency.com", "ENCY Post Division"},
        };
        for (String[] v : extraVendors) {
            users.add(userRepository.save(User.builder()
                    .username(v[0]).password(passwordEncoder.encode("vendor"))
                    .email(v[2]).fullName(v[1]).company(v[3])
                    .role(UserRole.VENDOR).build()));
        }

        return users;
    }

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @SuppressWarnings("unchecked")
    private List<Product> loadAndCreateProducts(List<User> users) {
        List<Product> products = new ArrayList<>();
        Random rng = new Random(42);

        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream is = new ClassPathResource("seed-sprutcam.json").getInputStream();
            List<Map<String, Object>> items = mapper.readValue(is, new TypeReference<>() {});

            int index = 0;
            for (Map<String, Object> item : items) {
                try {
                    Product p = buildProductFromSprutcam(item, index, users, rng);
                    products.add(productRepository.save(p));
                } catch (Exception e) {
                    log.warn("Skipping row {}: {}", index, e.getMessage());
                }
                index++;
            }
        } catch (Exception e) {
            log.error("Failed to load seed-sprutcam.json, falling back to minimal data", e);
            products.add(productRepository.save(buildFallbackProduct(users.get(1))));
        }

        return products;
    }

    private Product buildProductFromSprutcam(Map<String, Object> item, int index, List<User> users, Random rng) {
        String name = str(item, "name");
        String manufacturer = str(item, "machineManufacturer");
        String series = str(item, "machineSeries");
        String model = str(item, "machineModel");
        String ctrlMfr = str(item, "controllerManufacturer");
        String ctrlSeries = str(item, "controllerSeries");
        String ctrlModel = str(item, "controllerModel");
        String descriptionRaw = str(item, "description");
        String authorRaw = str(item, "author");
        String publisherRaw = str(item, "publisher");
        String createdDateStr = str(item, "createdDate");

        ContentType contentType = parseContentType(str(item, "type"));
        MachineType machineType = parseMachineType(str(item, "machineType"));
        int axes = item.get("axes") instanceof Number n ? n.intValue() : 0;
        ContentCategory category = machineType == MachineType.ROBOT
                ? ContentCategory.ROBOTS : ContentCategory.CNC_MACHINES;

        String description = descriptionRaw.isEmpty()
                ? generateDescription(manufacturer, model, series, category, machineType, axes, contentType)
                : descriptionRaw + ". " + generateDescription(manufacturer, model, series, category, machineType, axes, contentType);

        PublicationStatus pubStatus = pickPublicationStatus(rng);
        ExperienceStatus expStatus = pickExperienceStatus(rng);
        Visibility visibility = pickVisibility(pubStatus, rng);
        User owner = pickOwner(users, rng);

        BigDecimal price = generatePrice(contentType, category, rng);
        int trialDays = rng.nextBoolean() ? 30 : 14;
        int downloadCount = pubStatus == PublicationStatus.PUBLISHED ? rng.nextInt(500) : 0;

        String kitContents = contentType == ContentType.DIGITAL_MACHINE_KIT ? pickKitContents(rng) : null;
        String supportedCodes = generateSupportedCodes(category, machineType);
        String minSoftwareVersion = rng.nextInt(10) < 7 ? "SprutCAM X 17" : "SprutCAM X 16";

        LocalDateTime createdAt = parseCreatedDate(createdDateStr);
        LocalDateTime publishedAt = pubStatus == PublicationStatus.PUBLISHED
                ? createdAt.plusDays(rng.nextInt(30) + 1) : null;

        String imageUrl = pickImageUrl(manufacturer, category, machineType, index);
        String productOwner = publisherRaw.isEmpty() ? owner.getCompany() : publisherRaw;
        String authorName = authorRaw.isEmpty() ? owner.getFullName() : authorRaw;

        return Product.builder()
                .name(name)
                .contentType(contentType)
                .category(category)
                .description(description)
                .kitContents(kitContents)
                .minSoftwareVersion(minSoftwareVersion)
                .machineManufacturer(manufacturer)
                .machineSeries(series)
                .machineModel(model)
                .machineType(machineType)
                .numberOfAxes(axes)
                .controllerManufacturer(ctrlMfr)
                .controllerSeries(ctrlSeries)
                .controllerModel(ctrlModel)
                .priceEur(price)
                .productOwner(productOwner)
                .authorName(authorName)
                .trialDays(trialDays)
                .supportedCodes(supportedCodes)
                .imageUrl(imageUrl)
                .publicationStatus(pubStatus)
                .experienceStatus(expStatus)
                .visibility(visibility)
                .downloadCount(downloadCount)
                .owner(owner)
                .createdAt(createdAt)
                .publishedAt(publishedAt)
                .build();
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? "" : v.toString().trim();
    }

    private LocalDateTime parseCreatedDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return LocalDateTime.now().minusDays(365);
        try {
            return LocalDate.parse(dateStr, DATE_FMT).atStartOfDay();
        } catch (Exception e) {
            return LocalDateTime.now().minusDays(365);
        }
    }

    private ContentType parseContentType(String type) {
        return switch (type) {
            case "Post Processor" -> ContentType.POST_PROCESSOR;
            case "Machine Schema" -> ContentType.MACHINE_SCHEMA;
            case "Post Processor + Schema" -> ContentType.DIGITAL_MACHINE_KIT;
            default -> ContentType.POST_PROCESSOR;
        };
    }

    private MachineType parseMachineType(String type) {
        if (type == null || type.isBlank()) return MachineType.OTHER;
        return switch (type) {
            case "Milling" -> MachineType.MILLING;
            case "Turning" -> MachineType.TURNING;
            case "Mill-Turn" -> MachineType.MILL_TURN;
            case "EDM" -> MachineType.EDM;
            case "Swiss" -> MachineType.SWISS;
            case "Router" -> MachineType.ROUTER;
            case "Gas/Plasma/Laser" -> MachineType.GAS_PLASMA_LASER;
            case "Advanced Technology" -> MachineType.OTHER;
            case "MILLING" -> MachineType.MILLING;
            case "TURNING" -> MachineType.TURNING;
            case "MILL_TURN" -> MachineType.MILL_TURN;
            case "WIRE_EDM" -> MachineType.WIRE_EDM;
            case "LASER" -> MachineType.LASER;
            case "PLASMA" -> MachineType.PLASMA;
            case "WATERJET" -> MachineType.WATERJET;
            case "GRINDING" -> MachineType.GRINDING;
            case "ROBOT" -> MachineType.ROBOT;
            default -> MachineType.OTHER;
        };
    }

    private String generateDescription(
            String manufacturer, String model, String series,
            ContentCategory category, MachineType machineType,
            int axes, ContentType contentType
    ) {
        String machineTypeStr = machineTypeDescription(machineType);
        String axesStr = axes + "-axis";
        String categoryStr = category == ContentCategory.ROBOTS
                ? "industrial robot" : "CNC machine";

        String base = String.format(
                "The %s %s is a %s %s %s",
                manufacturer, model, axesStr, machineTypeStr, categoryStr
        );

        String contentDesc = switch (contentType) {
            case DIGITAL_MACHINE_KIT -> String.format(
                    ", and this page features its digital twin. " +
                    "The digital twin includes the machine's 3D model and kinematics. " +
                    "Designed specifically for programming the %s %s %s %s in SprutCAM X, " +
                    "this digital twin ensures optimal performance and accurate simulation.",
                    manufacturer, model, axesStr, machineTypeStr
            );
            case POST_PROCESSOR -> String.format(
                    ". This post processor generates optimized NC code for the %s %s. " +
                    "It supports all standard machining operations and has been validated " +
                    "for accurate toolpath translation and cycle time optimization.",
                    manufacturer, model
            );
            case MACHINE_SCHEMA -> String.format(
                    ". This machine schema provides the complete kinematic model for the %s %s, " +
                    "including axis limits, collision detection zones, and 3D geometry. " +
                    "Essential for accurate simulation and virtual commissioning.",
                    manufacturer, model
            );
            case INTERPRETER -> String.format(
                    ". This interpreter enables G-code simulation and verification for the %s %s. " +
                    "It accurately reproduces machine behavior for safe offline programming " +
                    "and program validation.",
                    manufacturer, model
            );
        };

        return base + contentDesc;
    }

    private String machineTypeDescription(MachineType type) {
        return switch (type) {
            case MILLING -> "Milling";
            case TURNING -> "Turning";
            case MILL_TURN -> "Mill-Turn";
            case WIRE_EDM -> "Wire EDM";
            case LASER -> "Laser cutting";
            case PLASMA -> "Plasma cutting";
            case WATERJET -> "Waterjet cutting";
            case GRINDING -> "Grinding";
            case ROBOT -> "Robot";
            case EDM -> "EDM";
            case ROUTER -> "Router";
            case SWISS -> "Swiss-type Turning";
            case GAS_PLASMA_LASER -> "Gas/Plasma/Laser cutting";
            case OTHER -> "Multi-purpose";
        };
    }

    private PublicationStatus pickPublicationStatus(Random rng) {
        int r = rng.nextInt(100);
        if (r < 65) return PublicationStatus.PUBLISHED;
        if (r < 80) return PublicationStatus.PENDING_REVIEW;
        if (r < 95) return PublicationStatus.DRAFT;
        return PublicationStatus.REJECTED;
    }

    private ExperienceStatus pickExperienceStatus(Random rng) {
        return rng.nextInt(100) < 40
                ? ExperienceStatus.VERIFIED_ON_EQUIPMENT
                : ExperienceStatus.NOT_TESTED;
    }

    private Visibility pickVisibility(PublicationStatus status, Random rng) {
        if (status == PublicationStatus.DRAFT) return Visibility.VENDOR;
        if (status == PublicationStatus.REJECTED) return Visibility.VENDOR;
        int r = rng.nextInt(100);
        if (r < 60) return Visibility.PUBLIC;
        if (r < 80) return Visibility.DEALERS;
        return Visibility.DEALER;
    }

    private User pickOwner(List<User> users, Random rng) {
        List<User> candidates = users.stream()
                .filter(u -> u.getRole() == UserRole.VENDOR || u.getRole() == UserRole.DEALER)
                .toList();
        return candidates.get(rng.nextInt(candidates.size()));
    }

    private BigDecimal generatePrice(ContentType contentType, ContentCategory category, Random rng) {
        int base = switch (contentType) {
            case DIGITAL_MACHINE_KIT -> category == ContentCategory.ROBOTS ? 1500 : 1000;
            case POST_PROCESSOR -> 600;
            case MACHINE_SCHEMA -> 800;
            case INTERPRETER -> 500;
        };
        int variation = rng.nextInt(base);
        return new BigDecimal(base + variation).setScale(0, RoundingMode.HALF_UP);
    }

    private String pickKitContents(Random rng) {
        String[][] options = {
                {"Schema", "Postprocessor"},
                {"Schema", "Postprocessor", "Interpreter"},
                {"Schema", "Postprocessor", "Sample Programs"},
                {"Schema", "Postprocessor", "Interpreter", "Sample Programs"},
        };
        return String.join(", ", options[rng.nextInt(options.length)]);
    }

    private String generateSupportedCodes(ContentCategory category, MachineType type) {
        if (category == ContentCategory.ROBOTS) {
            return null;
        }
        return switch (type) {
            case MILLING -> "G0, G1, G2, G3, G17, G18, G19, G28, G40, G41, G42, G43, G49, G54-G59, G80-G89, G90, G91\n" +
                    "M0, M1, M3, M4, M5, M6, M8, M9, M30";
            case TURNING -> "G0, G1, G2, G3, G28, G32, G40, G41, G42, G50, G54-G59, G70-G76, G90, G92, G94, G96, G97\n" +
                    "M0, M1, M3, M4, M5, M8, M9, M30, M41-M44";
            case MILL_TURN -> "G0, G1, G2, G3, G17, G18, G19, G28, G32, G40, G41, G42, G43, G50, G54-G59, G70-G76, G80-G89, G90, G91, G92, G96, G97\n" +
                    "M0, M1, M3, M4, M5, M6, M8, M9, M30, M41-M44";
            case WIRE_EDM, EDM -> "G0, G1, G2, G3, G41, G42, G54-G59, G90, G91, G92\n" +
                    "M0, M1, M2, M17, M20, M21, M30, M50, M60, M80";
            case LASER, GAS_PLASMA_LASER -> "G0, G1, G2, G3, G17, G40, G41, G42, G54-G59, G90, G91\n" +
                    "M0, M3, M5, M8, M9, M30";
            case GRINDING -> "G0, G1, G2, G3, G28, G54-G59, G90, G91\n" +
                    "M0, M3, M4, M5, M8, M9, M30";
            case SWISS -> "G0, G1, G2, G3, G28, G32, G40, G41, G42, G50, G54-G59, G70-G76, G90, G92, G96, G97\n" +
                    "M0, M1, M3, M4, M5, M8, M9, M30, M41-M44";
            case ROUTER -> "G0, G1, G2, G3, G17, G40, G41, G42, G43, G54-G59, G80-G89, G90, G91\n" +
                    "M0, M3, M5, M8, M9, M30";
            default -> "G0, G1, G2, G3, G90, G91\nM0, M3, M5, M30";
        };
    }

    // ========== IMAGE URL MAPPING ==========

    private static final Map<String, String[]> MANUFACTURER_IMAGES = new LinkedHashMap<>();

    private static final String[] FALLBACK_CNC = {
            "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6c/Small_CNC_Turning_Center.jpg/500px-Small_CNC_Turning_Center.jpg",
            "https://upload.wikimedia.org/wikipedia/commons/thumb/2/2a/Werkzeugmaschine.JPG/500px-Werkzeugmaschine.JPG",
            "https://upload.wikimedia.org/wikipedia/commons/thumb/6/67/DeckelMaho-DMU50e-MachiningCenter.jpg/500px-DeckelMaho-DMU50e-MachiningCenter.jpg",
            "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cb/Makino-S33-MachiningCenter.jpg/500px-Makino-S33-MachiningCenter.jpg",
            "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fb/FANUC_ROBODRILL_040.jpg/500px-FANUC_ROBODRILL_040.jpg",
            "https://upload.wikimedia.org/wikipedia/commons/thumb/6/65/C_32_U_RS3_1872.jpeg/500px-C_32_U_RS3_1872.jpeg",
    };

    private static final String[] FALLBACK_ROBOT = {
            "https://upload.wikimedia.org/wikipedia/commons/thumb/9/9b/FANUC_6-axis_welding_robots.jpg/500px-FANUC_6-axis_welding_robots.jpg",
            "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b0/KUKA_Industialroboter_IR_161.jpg/500px-KUKA_Industialroboter_IR_161.jpg",
            "https://upload.wikimedia.org/wikipedia/commons/thumb/6/69/ABB_welding_robot.jpg/500px-ABB_welding_robot.jpg",
            "https://upload.wikimedia.org/wikipedia/commons/thumb/5/57/Cobot.jpg/500px-Cobot.jpg",
            "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cf/Motoman_Industrie-Schweissroboter.jpg/500px-Motoman_Industrie-Schweissroboter.jpg",
            "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8b/Industry_Robot.jpg/500px-Industry_Robot.jpg",
            "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c8/FANUC_R2000iB_AtWork.jpg/500px-FANUC_R2000iB_AtWork.jpg",
            "https://upload.wikimedia.org/wikipedia/commons/thumb/9/91/FANUC_R-2000iB_series_robot_021.jpg/500px-FANUC_R-2000iB_series_robot_021.jpg",
    };

    // Shorthand constants for commonly used image URLs
    private static final String IMG_FANUC_WELD = "https://upload.wikimedia.org/wikipedia/commons/thumb/9/9b/FANUC_6-axis_welding_robots.jpg/500px-FANUC_6-axis_welding_robots.jpg";
    private static final String IMG_FANUC_R2000 = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c8/FANUC_R2000iB_AtWork.jpg/500px-FANUC_R2000iB_AtWork.jpg";
    private static final String IMG_FANUC_R2000_2 = "https://upload.wikimedia.org/wikipedia/commons/thumb/9/91/FANUC_R-2000iB_series_robot_021.jpg/500px-FANUC_R-2000iB_series_robot_021.jpg";
    private static final String IMG_KUKA_IR161 = "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b0/KUKA_Industialroboter_IR_161.jpg/500px-KUKA_Industialroboter_IR_161.jpg";
    private static final String IMG_KUKA_WRITER = "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d8/KUKA_Industrial_Robot_Writer.jpg/500px-KUKA_Industrial_Robot_Writer.jpg";
    private static final String IMG_KUKA_INNOROBO = "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e1/Innorobo_2015_-_Kuka_Robotics.JPG/500px-Innorobo_2015_-_Kuka_Robotics.JPG";
    private static final String IMG_ABB_WELD = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/69/ABB_welding_robot.jpg/500px-ABB_welding_robot.jpg";
    private static final String IMG_ABB_1 = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/17/Robot_ABB_1.jpg/500px-Robot_ABB_1.jpg";
    private static final String IMG_ABB_HIST = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a0/ASEA_and_ABB_industrial_robots.jpg/500px-ASEA_and_ABB_industrial_robots.jpg";
    private static final String IMG_MOTOMAN = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cf/Motoman_Industrie-Schweissroboter.jpg/500px-Motoman_Industrie-Schweissroboter.jpg";
    private static final String IMG_UR5E = "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7c/Robot_Lengan_Industri_UR5e.jpg/500px-Robot_Lengan_Industri_UR5e.jpg";
    private static final String IMG_COBOT = "https://upload.wikimedia.org/wikipedia/commons/thumb/5/57/Cobot.jpg/500px-Cobot.jpg";
    private static final String IMG_INDUSTRY = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8b/Industry_Robot.jpg/500px-Industry_Robot.jpg";
    private static final String IMG_CNC_TURN = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6c/Small_CNC_Turning_Center.jpg/500px-Small_CNC_Turning_Center.jpg";
    private static final String IMG_CNC_MILL = "https://upload.wikimedia.org/wikipedia/commons/thumb/2/2a/Werkzeugmaschine.JPG/500px-Werkzeugmaschine.JPG";
    private static final String IMG_DMG = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/67/DeckelMaho-DMU50e-MachiningCenter.jpg/500px-DeckelMaho-DMU50e-MachiningCenter.jpg";
    private static final String IMG_MAKINO_S33 = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cb/Makino-S33-MachiningCenter.jpg/500px-Makino-S33-MachiningCenter.jpg";
    private static final String IMG_MAKINO_IQ = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a4/Makino_iQ300.jpg/500px-Makino_iQ300.jpg";
    private static final String IMG_ROBODRILL = "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fb/FANUC_ROBODRILL_040.jpg/500px-FANUC_ROBODRILL_040.jpg";
    private static final String IMG_ROBODRILL2 = "https://upload.wikimedia.org/wikipedia/commons/thumb/b/bf/FANUC_ROBODRILL_052.jpg/500px-FANUC_ROBODRILL_052.jpg";
    private static final String IMG_HERMLE = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/65/C_32_U_RS3_1872.jpeg/500px-C_32_U_RS3_1872.jpeg";
    private static final String IMG_HERMLE2 = "https://upload.wikimedia.org/wikipedia/commons/thumb/7/79/Bearbeitungszentrum_Schnittmodell_Hermle_01.jpg/500px-Bearbeitungszentrum_Schnittmodell_Hermle_01.jpg";
    private static final String IMG_TRUMPF1 = "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f8/Trumpf_TruLaser_Center_5030_2021-07-07.jpg/500px-Trumpf_TruLaser_Center_5030_2021-07-07.jpg";
    private static final String IMG_TRUMPF2 = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cf/Trumpf_TruLaser_Center_7030_2020-10-13.jpg/500px-Trumpf_TruLaser_Center_7030_2020-10-13.jpg";
    private static final String IMG_LASER = "https://upload.wikimedia.org/wikipedia/commons/thumb/7/79/CNC_Laser_Cutting_Machine.jpg/500px-CNC_Laser_Cutting_Machine.jpg";
    private static final String IMG_MAZAK1 = "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b9/YAMAZAKI_MAZAK_Multi-Tasking_Machine_INTEGREX_i-200S_in_THE_YAMAZAKI_MAZAK_MUSEUM_OF_MACHINE_TOOLS_November_8%2C_2019_01.jpg/500px-YAMAZAKI_MAZAK_Multi-Tasking_Machine_INTEGREX_i-200S_in_THE_YAMAZAKI_MAZAK_MUSEUM_OF_MACHINE_TOOLS_November_8%2C_2019_01.jpg";
    private static final String IMG_MAZAK2 = "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d2/YAMAZAKI_MAZAK_Multi-Tasking_Machine_INTEGREX_i-200S_in_THE_YAMAZAKI_MAZAK_MUSEUM_OF_MACHINE_TOOLS_November_8%2C_2019_03.jpg/500px-YAMAZAKI_MAZAK_Multi-Tasking_Machine_INTEGREX_i-200S_in_THE_YAMAZAKI_MAZAK_MUSEUM_OF_MACHINE_TOOLS_November_8%2C_2019_03.jpg";
    private static final String IMG_OKUMA1 = "https://upload.wikimedia.org/wikipedia/commons/thumb/b/bf/Okuma_MULTUS_U3000_multi-tasking_machine_with_automatic_tool_changer.jpg/500px-Okuma_MULTUS_U3000_multi-tasking_machine_with_automatic_tool_changer.jpg";
    private static final String IMG_OKUMA2 = "https://upload.wikimedia.org/wikipedia/commons/thumb/5/53/Okuma_MULTUS_U3000_multi-tasking_machine_with_automatic_tool_changer_2.jpg/500px-Okuma_MULTUS_U3000_multi-tasking_machine_with_automatic_tool_changer_2.jpg";

    static {
        // ─── CNC — brand-specific real photos ───────────────────────────
        MANUFACTURER_IMAGES.put("HAAS",     new String[]{ IMG_CNC_TURN, IMG_CNC_MILL, IMG_ROBODRILL });
        MANUFACTURER_IMAGES.put("DMG MORI", new String[]{ IMG_DMG, IMG_CNC_MILL, IMG_HERMLE });
        MANUFACTURER_IMAGES.put("Mazak",    new String[]{ IMG_MAZAK1, IMG_MAZAK2 });
        MANUFACTURER_IMAGES.put("Okuma",    new String[]{ IMG_OKUMA1, IMG_OKUMA2 });
        MANUFACTURER_IMAGES.put("Makino",   new String[]{ IMG_MAKINO_S33, IMG_MAKINO_IQ });
        MANUFACTURER_IMAGES.put("FANUC_CNC", new String[]{ IMG_ROBODRILL, IMG_ROBODRILL2 });
        MANUFACTURER_IMAGES.put("Hermle",   new String[]{ IMG_HERMLE, IMG_HERMLE2 });
        MANUFACTURER_IMAGES.put("Trumpf",   new String[]{ IMG_TRUMPF1, IMG_TRUMPF2 });

        // CNC — no brand photos, match by equipment type
        MANUFACTURER_IMAGES.put("Doosan",   new String[]{ IMG_CNC_TURN, IMG_CNC_MILL, IMG_MAKINO_S33 });
        MANUFACTURER_IMAGES.put("Brother",  new String[]{ IMG_ROBODRILL, IMG_CNC_MILL });
        MANUFACTURER_IMAGES.put("Hurco",    new String[]{ IMG_MAKINO_S33, IMG_CNC_MILL });
        MANUFACTURER_IMAGES.put("Studer",   new String[]{ IMG_CNC_TURN, IMG_HERMLE2 });
        MANUFACTURER_IMAGES.put("GF Machining Solutions", new String[]{ IMG_HERMLE, IMG_MAKINO_IQ });
        MANUFACTURER_IMAGES.put("INDEX",    new String[]{ IMG_CNC_TURN, IMG_DMG });
        MANUFACTURER_IMAGES.put("Chiron",   new String[]{ IMG_HERMLE, IMG_DMG });
        MANUFACTURER_IMAGES.put("Grob",     new String[]{ IMG_DMG, IMG_HERMLE });
        MANUFACTURER_IMAGES.put("EMAG",     new String[]{ IMG_CNC_TURN, IMG_CNC_MILL });
        MANUFACTURER_IMAGES.put("Bystronic", new String[]{ IMG_LASER, IMG_TRUMPF1 });
        MANUFACTURER_IMAGES.put("AMADA",    new String[]{ IMG_LASER, IMG_TRUMPF2 });
        MANUFACTURER_IMAGES.put("Sodick",   new String[]{ IMG_MAKINO_IQ, IMG_MAKINO_S33 });
        MANUFACTURER_IMAGES.put("Tormach",  new String[]{ IMG_ROBODRILL2, IMG_CNC_TURN });

        // ─── Robots — brands WITH real Wikimedia photos ─────────────────
        MANUFACTURER_IMAGES.put("FANUC_ROBOT", new String[]{ IMG_FANUC_WELD, IMG_FANUC_R2000, IMG_FANUC_R2000_2 });
        MANUFACTURER_IMAGES.put("KUKA",     new String[]{ IMG_KUKA_IR161, IMG_KUKA_INNOROBO, IMG_KUKA_WRITER });
        MANUFACTURER_IMAGES.put("ABB",      new String[]{ IMG_ABB_WELD, IMG_ABB_1, IMG_ABB_HIST });
        MANUFACTURER_IMAGES.put("Yaskawa",  new String[]{ IMG_MOTOMAN, IMG_INDUSTRY });
        MANUFACTURER_IMAGES.put("Universal Robots", new String[]{ IMG_UR5E, IMG_COBOT });

        // ─── Robots — large industrial (welding/handling) ───────────────
        MANUFACTURER_IMAGES.put("Kawasaki",          new String[]{ IMG_INDUSTRY, IMG_FANUC_R2000 });
        MANUFACTURER_IMAGES.put("Hyundai Robotics",  new String[]{ IMG_FANUC_WELD, IMG_KUKA_IR161 });
        MANUFACTURER_IMAGES.put("Panasonic",         new String[]{ IMG_MOTOMAN, IMG_ABB_WELD });
        MANUFACTURER_IMAGES.put("OTC Daihen",        new String[]{ IMG_MOTOMAN, IMG_FANUC_WELD });
        MANUFACTURER_IMAGES.put("IGM",               new String[]{ IMG_ABB_WELD, IMG_MOTOMAN });
        MANUFACTURER_IMAGES.put("Comau",             new String[]{ IMG_KUKA_INNOROBO, IMG_INDUSTRY });
        MANUFACTURER_IMAGES.put("Nachi",             new String[]{ IMG_FANUC_R2000_2, IMG_INDUSTRY });
        MANUFACTURER_IMAGES.put("Estun",             new String[]{ IMG_FANUC_R2000, IMG_INDUSTRY, IMG_KUKA_IR161 });
        MANUFACTURER_IMAGES.put("Siasun",            new String[]{ IMG_INDUSTRY, IMG_FANUC_R2000_2 });
        MANUFACTURER_IMAGES.put("Rainbow Robotics",  new String[]{ IMG_FANUC_R2000_2, IMG_COBOT });

        // ─── Robots — collaborative (cobots) ────────────────────────────
        MANUFACTURER_IMAGES.put("Dobot",             new String[]{ IMG_COBOT, IMG_UR5E });
        MANUFACTURER_IMAGES.put("Franka Emika",      new String[]{ IMG_UR5E, IMG_COBOT });
        MANUFACTURER_IMAGES.put("Doosan Robotics",   new String[]{ IMG_COBOT, IMG_ABB_1 });
        MANUFACTURER_IMAGES.put("JAKA",              new String[]{ IMG_UR5E, IMG_INDUSTRY });
        MANUFACTURER_IMAGES.put("AUBO",              new String[]{ IMG_COBOT, IMG_FANUC_R2000 });
        MANUFACTURER_IMAGES.put("Elite Robots",      new String[]{ IMG_UR5E, IMG_COBOT });
        MANUFACTURER_IMAGES.put("Rokae",             new String[]{ IMG_COBOT, IMG_ABB_1 });
        MANUFACTURER_IMAGES.put("Kassow Robots",     new String[]{ IMG_UR5E, IMG_INDUSTRY });
        MANUFACTURER_IMAGES.put("Hanwha",            new String[]{ IMG_COBOT, IMG_UR5E });
        MANUFACTURER_IMAGES.put("Techman Robot (Omron)", new String[]{ IMG_UR5E, IMG_COBOT });
        MANUFACTURER_IMAGES.put("Flexiv",            new String[]{ IMG_COBOT, IMG_ABB_1 });
        MANUFACTURER_IMAGES.put("Precise Automation", new String[]{ IMG_UR5E, IMG_INDUSTRY });

        // ─── Robots — precision / small ─────────────────────────────────
        MANUFACTURER_IMAGES.put("Stäubli",    new String[]{ IMG_ABB_1, IMG_INDUSTRY });
        MANUFACTURER_IMAGES.put("Denso",      new String[]{ IMG_INDUSTRY, IMG_ABB_1 });
        MANUFACTURER_IMAGES.put("Epson",      new String[]{ IMG_ABB_1, IMG_COBOT });
        MANUFACTURER_IMAGES.put("Mecademic",  new String[]{ IMG_COBOT, IMG_ABB_1 });
    }

    private String pickImageUrl(String manufacturer, ContentCategory category, MachineType machineType, int index) {
        String key = manufacturer;
        if ("FANUC".equals(manufacturer)) {
            key = category == ContentCategory.ROBOTS ? "FANUC_ROBOT" : "FANUC_CNC";
        }
        if ("Mitsubishi Electric".equals(manufacturer)) {
            key = category == ContentCategory.ROBOTS ? "Nachi" : "Sodick";
        }

        String[] images = MANUFACTURER_IMAGES.get(key);

        if (images == null) {
            for (Map.Entry<String, String[]> entry : MANUFACTURER_IMAGES.entrySet()) {
                if (manufacturer.toLowerCase().contains(entry.getKey().toLowerCase()) ||
                        entry.getKey().toLowerCase().contains(manufacturer.toLowerCase())) {
                    images = entry.getValue();
                    break;
                }
            }
        }

        if (images == null) {
            images = category == ContentCategory.ROBOTS ? FALLBACK_ROBOT : FALLBACK_CNC;
        }

        int hash = Math.abs((manufacturer + index).hashCode());
        return images[hash % images.length];
    }

    private Product buildFallbackProduct(User owner) {
        return Product.builder()
                .name("Digital Machine Kit for HAAS VF-2")
                .contentType(ContentType.DIGITAL_MACHINE_KIT)
                .category(ContentCategory.CNC_MACHINES)
                .description("Fallback demo product.")
                .machineManufacturer("HAAS")
                .machineSeries("VF")
                .machineModel("VF-2")
                .machineType(MachineType.MILLING)
                .numberOfAxes(3)
                .priceEur(new BigDecimal("1000"))
                .imageUrl(pickImageUrl("HAAS", ContentCategory.CNC_MACHINES, MachineType.MILLING, 0))
                .publicationStatus(PublicationStatus.PUBLISHED)
                .visibility(Visibility.PUBLIC)
                .downloadCount(0)
                .owner(owner)
                .build();
    }
}
