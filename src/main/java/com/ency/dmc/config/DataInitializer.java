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
import java.time.LocalDateTime;
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

    @SuppressWarnings("unchecked")
    private List<Product> loadAndCreateProducts(List<User> users) {
        List<Product> products = new ArrayList<>();
        Random rng = new Random(42);

        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream is = new ClassPathResource("seed-machines.json").getInputStream();
            List<Map<String, Object>> manufacturers = mapper.readValue(is, new TypeReference<>() {});

            int index = 0;
            for (Map<String, Object> mfr : manufacturers) {
                String manufacturer = (String) mfr.get("mfr");
                String ctrlMfr = (String) mfr.get("ctrlMfr");
                String ctrlSeries = (String) mfr.get("ctrlSeries");
                String ctrlModel = (String) mfr.get("ctrlModel");
                String catStr = (String) mfr.get("cat");
                ContentCategory category = "ROBOT".equals(catStr)
                        ? ContentCategory.ROBOTS : ContentCategory.CNC_MACHINES;

                List<Map<String, Object>> groups = (List<Map<String, Object>>) mfr.get("groups");
                for (Map<String, Object> group : groups) {
                    String series = (String) group.get("series");
                    String typeStr = (String) group.get("type");
                    MachineType machineType = parseMachineType(typeStr);
                    int axes = ((Number) group.get("axes")).intValue();

                    String groupCtrlMfr = group.containsKey("ctrlMfr") ? (String) group.get("ctrlMfr") : ctrlMfr;
                    String groupCtrlSeries = group.containsKey("ctrlSeries") ? (String) group.get("ctrlSeries") : ctrlSeries;
                    String groupCtrlModel = group.containsKey("ctrlModel") ? (String) group.get("ctrlModel") : ctrlModel;

                    List<String> models = (List<String>) group.get("models");
                    for (String model : models) {
                        Product p = buildProduct(
                                index, manufacturer, series, model, category, machineType, axes,
                                groupCtrlMfr, groupCtrlSeries, groupCtrlModel,
                                users, rng
                        );
                        products.add(productRepository.save(p));
                        index++;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to load seed-machines.json, falling back to minimal data", e);
            products.add(productRepository.save(buildFallbackProduct(users.get(1))));
        }

        return products;
    }

    private Product buildProduct(
            int index, String manufacturer, String series, String model,
            ContentCategory category, MachineType machineType, int axes,
            String ctrlMfr, String ctrlSeries, String ctrlModel,
            List<User> users, Random rng
    ) {
        ContentType contentType = pickContentType(index, category, rng);
        String contentTypeLabel = contentTypeLabel(contentType);
        String productName = contentTypeLabel + " for " + manufacturer + " " + model;

        String description = generateDescription(
                manufacturer, model, series, category, machineType, axes, contentType
        );

        PublicationStatus pubStatus = pickPublicationStatus(rng);
        ExperienceStatus expStatus = pickExperienceStatus(rng);
        Visibility visibility = pickVisibility(pubStatus, rng);
        User owner = pickOwner(users, rng);

        BigDecimal price = generatePrice(contentType, category, rng);
        int trialDays = rng.nextBoolean() ? 30 : 14;
        int downloadCount = pubStatus == PublicationStatus.PUBLISHED
                ? rng.nextInt(500) : 0;

        String kitContents = contentType == ContentType.DIGITAL_MACHINE_KIT
                ? pickKitContents(rng) : null;

        String supportedCodes = generateSupportedCodes(category, machineType);

        LocalDateTime publishedAt = pubStatus == PublicationStatus.PUBLISHED
                ? LocalDateTime.now().minusDays(rng.nextInt(365) + 1) : null;

        String minSoftwareVersion = rng.nextInt(10) < 7 ? "ENCY 2.0" : "ENCY 1.8";

        String imageUrl = pickImageUrl(manufacturer, category, machineType, index);

        return Product.builder()
                .name(productName)
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
                .productOwner(owner.getCompany())
                .authorName(owner.getFullName())
                .trialDays(trialDays)
                .supportedCodes(supportedCodes)
                .imageUrl(imageUrl)
                .publicationStatus(pubStatus)
                .experienceStatus(expStatus)
                .visibility(visibility)
                .downloadCount(downloadCount)
                .owner(owner)
                .publishedAt(publishedAt)
                .build();
    }

    private MachineType parseMachineType(String type) {
        return switch (type) {
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

    private ContentType pickContentType(int index, ContentCategory category, Random rng) {
        if (category == ContentCategory.ROBOTS) {
            return switch (index % 5) {
                case 0, 1 -> ContentType.DIGITAL_MACHINE_KIT;
                case 2 -> ContentType.POST_PROCESSOR;
                case 3 -> ContentType.MACHINE_SCHEMA;
                default -> ContentType.INTERPRETER;
            };
        }
        return switch (rng.nextInt(10)) {
            case 0, 1, 2 -> ContentType.POST_PROCESSOR;
            case 3, 4, 5 -> ContentType.DIGITAL_MACHINE_KIT;
            case 6, 7 -> ContentType.MACHINE_SCHEMA;
            default -> ContentType.INTERPRETER;
        };
    }

    private String contentTypeLabel(ContentType ct) {
        return switch (ct) {
            case POST_PROCESSOR -> "Post Processor";
            case DIGITAL_MACHINE_KIT -> "Digital Machine Kit";
            case MACHINE_SCHEMA -> "Machine Schema";
            case INTERPRETER -> "Interpreter";
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
            case WIRE_EDM -> "G0, G1, G2, G3, G41, G42, G54-G59, G90, G91, G92\n" +
                    "M0, M1, M2, M17, M20, M21, M30, M50, M60, M80";
            case LASER -> "G0, G1, G2, G3, G17, G40, G41, G42, G54-G59, G90, G91\n" +
                    "M0, M3, M5, M8, M9, M30";
            case GRINDING -> "G0, G1, G2, G3, G28, G54-G59, G90, G91\n" +
                    "M0, M3, M4, M5, M8, M9, M30";
            default -> "G0, G1, G2, G3, G90, G91\nM0, M3, M5, M30";
        };
    }

    // ========== IMAGE URL MAPPING ==========

    private static final Map<String, String[]> MANUFACTURER_IMAGES = new LinkedHashMap<>();
    private static final Map<MachineType, String> TYPE_DEFAULT_IMAGES = new LinkedHashMap<>();

    static {
        // Generic fallbacks
        final String IMG_CNC_TURNING = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6c/Small_CNC_Turning_Center.jpg/960px-Small_CNC_Turning_Center.jpg";
        final String IMG_OKUMA_MULTUS = "https://upload.wikimedia.org/wikipedia/commons/thumb/5/53/Okuma_MULTUS_U3000_multi-tasking_machine_with_automatic_tool_changer_2.jpg/960px-Okuma_MULTUS_U3000_multi-tasking_machine_with_automatic_tool_changer_2.jpg";
        final String IMG_LASER_CUT = "https://upload.wikimedia.org/wikipedia/commons/thumb/7/79/CNC_Laser_Cutting_Machine.jpg/960px-CNC_Laser_Cutting_Machine.jpg";

        // --- ROBOT MANUFACTURERS ---
        MANUFACTURER_IMAGES.put("FANUC_ROBOT", new String[]{
                "https://upload.wikimedia.org/wikipedia/commons/thumb/9/9b/FANUC_6-axis_welding_robots.jpg/960px-FANUC_6-axis_welding_robots.jpg",
                "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c8/FANUC_R2000iB_AtWork.jpg/960px-FANUC_R2000iB_AtWork.jpg",
                "https://upload.wikimedia.org/wikipedia/commons/thumb/9/91/FANUC_R-2000iB_series_robot_021.jpg/960px-FANUC_R-2000iB_series_robot_021.jpg"
        });
        MANUFACTURER_IMAGES.put("KUKA", new String[]{
                "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b0/KUKA_Industialroboter_IR_161.jpg/960px-KUKA_Industialroboter_IR_161.jpg",
                "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e1/Innorobo_2015_-_Kuka_Robotics.JPG/960px-Innorobo_2015_-_Kuka_Robotics.JPG"
        });
        MANUFACTURER_IMAGES.put("ABB", new String[]{
                "https://upload.wikimedia.org/wikipedia/commons/thumb/6/69/ABB_welding_robot.jpg/960px-ABB_welding_robot.jpg",
                "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a0/ASEA_and_ABB_industrial_robots.jpg/960px-ASEA_and_ABB_industrial_robots.jpg"
        });
        MANUFACTURER_IMAGES.put("Yaskawa", new String[]{
                "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cf/Motoman_Industrie-Schweissroboter.jpg/960px-Motoman_Industrie-Schweissroboter.jpg",
                "https://upload.wikimedia.org/wikipedia/commons/e/e5/Robotworx-plasma-cutting-robot.jpg"
        });
        MANUFACTURER_IMAGES.put("Universal Robots", new String[]{
                "https://upload.wikimedia.org/wikipedia/commons/thumb/1/16/UR16e_robot_arm.png/960px-UR16e_robot_arm.png",
                "https://upload.wikimedia.org/wikipedia/commons/thumb/5/57/Cobot.jpg/960px-Cobot.jpg"
        });
        MANUFACTURER_IMAGES.put("Siasun", new String[]{
                "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8b/Industry_Robot.jpg/837px-Industry_Robot.jpg"
        });

        // --- CNC MACHINE MANUFACTURERS ---
        MANUFACTURER_IMAGES.put("HAAS", new String[]{
                IMG_CNC_TURNING,
                IMG_OKUMA_MULTUS
        });
        MANUFACTURER_IMAGES.put("DMG MORI", new String[]{
                "https://upload.wikimedia.org/wikipedia/commons/thumb/6/67/DeckelMaho-DMU50e-MachiningCenter.jpg/960px-DeckelMaho-DMU50e-MachiningCenter.jpg",
                "https://upload.wikimedia.org/wikipedia/commons/thumb/2/2a/Werkzeugmaschine.JPG/960px-Werkzeugmaschine.JPG"
        });
        MANUFACTURER_IMAGES.put("Mazak", new String[]{
                "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b9/YAMAZAKI_MAZAK_Multi-Tasking_Machine_INTEGREX_i-200S_in_THE_YAMAZAKI_MAZAK_MUSEUM_OF_MACHINE_TOOLS_November_8%2C_2019_01.jpg/960px-YAMAZAKI_MAZAK_Multi-Tasking_Machine_INTEGREX_i-200S_in_THE_YAMAZAKI_MAZAK_MUSEUM_OF_MACHINE_TOOLS_November_8%2C_2019_01.jpg",
                "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d2/YAMAZAKI_MAZAK_Multi-Tasking_Machine_INTEGREX_i-200S_in_THE_YAMAZAKI_MAZAK_MUSEUM_OF_MACHINE_TOOLS_November_8%2C_2019_03.jpg/960px-YAMAZAKI_MAZAK_Multi-Tasking_Machine_INTEGREX_i-200S_in_THE_YAMAZAKI_MAZAK_MUSEUM_OF_MACHINE_TOOLS_November_8%2C_2019_03.jpg"
        });
        MANUFACTURER_IMAGES.put("Okuma", new String[]{
                "https://upload.wikimedia.org/wikipedia/commons/thumb/b/bf/Okuma_MULTUS_U3000_multi-tasking_machine_with_automatic_tool_changer.jpg/960px-Okuma_MULTUS_U3000_multi-tasking_machine_with_automatic_tool_changer.jpg",
                IMG_OKUMA_MULTUS
        });
        MANUFACTURER_IMAGES.put("Makino", new String[]{
                "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cb/Makino-S33-MachiningCenter.jpg/960px-Makino-S33-MachiningCenter.jpg",
                "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a4/Makino_iQ300.jpg/960px-Makino_iQ300.jpg"
        });
        MANUFACTURER_IMAGES.put("FANUC_CNC", new String[]{
                "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fb/FANUC_ROBODRILL_040.jpg/960px-FANUC_ROBODRILL_040.jpg",
                "https://upload.wikimedia.org/wikipedia/commons/thumb/b/bf/FANUC_ROBODRILL_052.jpg/960px-FANUC_ROBODRILL_052.jpg"
        });
        MANUFACTURER_IMAGES.put("Hermle", new String[]{
                "https://upload.wikimedia.org/wikipedia/commons/thumb/6/65/C_32_U_RS3_1872.jpeg/960px-C_32_U_RS3_1872.jpeg",
                "https://upload.wikimedia.org/wikipedia/commons/thumb/7/79/Bearbeitungszentrum_Schnittmodell_Hermle_01.jpg/960px-Bearbeitungszentrum_Schnittmodell_Hermle_01.jpg"
        });
        MANUFACTURER_IMAGES.put("Trumpf", new String[]{
                "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f8/Trumpf_TruLaser_Center_5030_2021-07-07.jpg/960px-Trumpf_TruLaser_Center_5030_2021-07-07.jpg",
                "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cf/Trumpf_TruLaser_Center_7030_2020-10-13.jpg/960px-Trumpf_TruLaser_Center_7030_2020-10-13.jpg"
        });
        MANUFACTURER_IMAGES.put("Doosan", new String[]{
                IMG_CNC_TURNING,
                IMG_OKUMA_MULTUS
        });

        // --- DEFAULT IMAGES BY MACHINE TYPE ---
        TYPE_DEFAULT_IMAGES.put(MachineType.MILLING, IMG_CNC_TURNING);
        TYPE_DEFAULT_IMAGES.put(MachineType.TURNING, IMG_CNC_TURNING);
        TYPE_DEFAULT_IMAGES.put(MachineType.MILL_TURN, IMG_OKUMA_MULTUS);
        TYPE_DEFAULT_IMAGES.put(MachineType.WIRE_EDM, IMG_CNC_TURNING);
        TYPE_DEFAULT_IMAGES.put(MachineType.LASER, IMG_LASER_CUT);
        TYPE_DEFAULT_IMAGES.put(MachineType.GRINDING, IMG_CNC_TURNING);
        TYPE_DEFAULT_IMAGES.put(MachineType.ROBOT, "https://upload.wikimedia.org/wikipedia/commons/thumb/9/9b/FANUC_6-axis_welding_robots.jpg/960px-FANUC_6-axis_welding_robots.jpg");
        TYPE_DEFAULT_IMAGES.put(MachineType.OTHER, IMG_CNC_TURNING);
        TYPE_DEFAULT_IMAGES.put(MachineType.PLASMA, IMG_LASER_CUT);
        TYPE_DEFAULT_IMAGES.put(MachineType.WATERJET, IMG_LASER_CUT);
    }

    private String pickImageUrl(String manufacturer, ContentCategory category, MachineType machineType, int index) {
        String key = manufacturer;
        if ("FANUC".equals(manufacturer)) {
            key = category == ContentCategory.ROBOTS ? "FANUC_ROBOT" : "FANUC_CNC";
        }

        String[] images = MANUFACTURER_IMAGES.get(key);
        if (images != null && images.length > 0) {
            return images[index % images.length];
        }

        for (Map.Entry<String, String[]> entry : MANUFACTURER_IMAGES.entrySet()) {
            if (manufacturer.toLowerCase().contains(entry.getKey().toLowerCase()) ||
                    entry.getKey().toLowerCase().contains(manufacturer.toLowerCase())) {
                images = entry.getValue();
                if (images.length > 0) return images[index % images.length];
            }
        }

        String defaultImg = TYPE_DEFAULT_IMAGES.get(machineType);
        return defaultImg != null ? defaultImg
                : "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6c/Small_CNC_Turning_Center.jpg/960px-Small_CNC_Turning_Center.jpg";
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
