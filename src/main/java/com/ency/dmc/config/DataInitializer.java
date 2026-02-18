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

    // ========== IMAGE URL POOLS ==========

    private static final String[] ROBOT_IMAGES = {
            "https://images.unsplash.com/photo-1563203369-26f2e4a5ccf7?w=600",
            "https://images.unsplash.com/photo-1485827404703-89b55fcc595e?w=600",
            "https://images.unsplash.com/photo-1561557944-6e7860d1a7eb?w=600",
            "https://images.unsplash.com/photo-1518314916381-77a37c2a49ae?w=600",
            "https://images.unsplash.com/photo-1558618666-fcd25c85f82e?w=600",
            "https://images.unsplash.com/photo-1581091226033-d5c48150dbaa?w=600",
            "https://images.unsplash.com/photo-1531746790095-e74972679037?w=600",
            "https://images.unsplash.com/photo-1589254065878-42c014d0f398?w=600",
            "https://images.unsplash.com/photo-1581092160607-ee22621dd758?w=600",
            "https://images.unsplash.com/photo-1581092918056-0c4c3acd3789?w=600",
            "https://images.unsplash.com/photo-1581092795360-fd1ca04f0952?w=600",
            "https://images.unsplash.com/photo-1581093588401-fbb62a02f120?w=600",
            "https://images.unsplash.com/photo-1581093450021-4a7360e9a6b5?w=600",
            "https://images.unsplash.com/photo-1581093806997-124204d9fa9d?w=600",
            "https://images.unsplash.com/photo-1601132359864-c974e79890ac?w=600",
            "https://images.unsplash.com/photo-1547394765-185e1e68f34e?w=600",
    };

    private static final String[] MILLING_IMAGES = {
            "https://images.unsplash.com/photo-1565043589221-1a6fd9ae45c7?w=600",
            "https://images.unsplash.com/photo-1504917595217-d4dc5ebe6122?w=600",
            "https://images.unsplash.com/photo-1581092160607-ee22621dd758?w=600",
            "https://images.unsplash.com/photo-1621905252507-b35492cc74b4?w=600",
            "https://images.unsplash.com/photo-1586864387789-628af9feed72?w=600",
            "https://images.unsplash.com/photo-1567789884554-0b844b597180?w=600",
            "https://images.unsplash.com/photo-1537462715879-360eeb61a0ad?w=600",
            "https://images.unsplash.com/photo-1624365169364-0640dd10e180?w=600",
            "https://images.unsplash.com/photo-1562408590-e32931084e23?w=600",
            "https://images.unsplash.com/photo-1581091226825-a6a2a5aee158?w=600",
            "https://images.unsplash.com/photo-1590959651373-a3db0f38a961?w=600",
            "https://images.unsplash.com/photo-1717386255773-1e3037c81c9a?w=600",
    };

    private static final String[] TURNING_IMAGES = {
            "https://images.unsplash.com/photo-1504917595217-d4dc5ebe6122?w=600",
            "https://images.unsplash.com/photo-1565043589221-1a6fd9ae45c7?w=600",
            "https://images.unsplash.com/photo-1537462715879-360eeb61a0ad?w=600",
            "https://images.unsplash.com/photo-1567789884554-0b844b597180?w=600",
            "https://images.unsplash.com/photo-1562408590-e32931084e23?w=600",
            "https://images.unsplash.com/photo-1590959651373-a3db0f38a961?w=600",
            "https://images.unsplash.com/photo-1624365169364-0640dd10e180?w=600",
            "https://images.unsplash.com/photo-1586864387789-628af9feed72?w=600",
            "https://images.unsplash.com/photo-1581091226825-a6a2a5aee158?w=600",
            "https://images.unsplash.com/photo-1717386255773-1e3037c81c9a?w=600",
    };

    private static final String[] LASER_IMAGES = {
            "https://images.unsplash.com/photo-1558611848-73f7eb4001a1?w=600",
            "https://images.unsplash.com/photo-1504917595217-d4dc5ebe6122?w=600",
            "https://images.unsplash.com/photo-1565043589221-1a6fd9ae45c7?w=600",
            "https://images.unsplash.com/photo-1581092160607-ee22621dd758?w=600",
            "https://images.unsplash.com/photo-1621905252507-b35492cc74b4?w=600",
            "https://images.unsplash.com/photo-1537462715879-360eeb61a0ad?w=600",
            "https://images.unsplash.com/photo-1624365169364-0640dd10e180?w=600",
            "https://images.unsplash.com/photo-1590959651373-a3db0f38a961?w=600",
    };

    private static final String[] GRINDING_IMAGES = {
            "https://images.unsplash.com/photo-1504917595217-d4dc5ebe6122?w=600",
            "https://images.unsplash.com/photo-1537462715879-360eeb61a0ad?w=600",
            "https://images.unsplash.com/photo-1565043589221-1a6fd9ae45c7?w=600",
            "https://images.unsplash.com/photo-1567789884554-0b844b597180?w=600",
            "https://images.unsplash.com/photo-1562408590-e32931084e23?w=600",
            "https://images.unsplash.com/photo-1586864387789-628af9feed72?w=600",
    };

    private static final String[] WIRE_EDM_IMAGES = {
            "https://images.unsplash.com/photo-1565043589221-1a6fd9ae45c7?w=600",
            "https://images.unsplash.com/photo-1537462715879-360eeb61a0ad?w=600",
            "https://images.unsplash.com/photo-1504917595217-d4dc5ebe6122?w=600",
            "https://images.unsplash.com/photo-1562408590-e32931084e23?w=600",
            "https://images.unsplash.com/photo-1586864387789-628af9feed72?w=600",
            "https://images.unsplash.com/photo-1624365169364-0640dd10e180?w=600",
    };

    private String pickImageUrl(String manufacturer, ContentCategory category, MachineType machineType, int index) {
        int hash = Math.abs((manufacturer + index).hashCode());
        String[] pool = switch (machineType) {
            case ROBOT -> ROBOT_IMAGES;
            case MILLING -> MILLING_IMAGES;
            case TURNING -> TURNING_IMAGES;
            case MILL_TURN -> MILLING_IMAGES;
            case LASER, PLASMA, WATERJET -> LASER_IMAGES;
            case WIRE_EDM -> WIRE_EDM_IMAGES;
            case GRINDING -> GRINDING_IMAGES;
            case OTHER -> MILLING_IMAGES;
        };
        return pool[hash % pool.length];
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
