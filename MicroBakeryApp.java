import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.stream.Collectors;

/**
 * Crumb & Whisk - Micro Bakery Pre-order Application (Java Swing).
 *
 * OOP concepts demonstrated:
 *  - Abstraction:    abstract class Product, interface DietCompatible
 *  - Inheritance:    BakeryItem and CustomCake extend Product
 *  - Polymorphism:   getDescription(), getRecipe(), supports() behave differently per subclass
 *  - Encapsulation:  private fields + getters/setters in every model class
 *  - Composition:    Order has OrderItems, OrderItem has a Product, Cart has OrderItems
 *  - Enums:          Category, Diet, OrderStatus, PaymentMethod, PaymentStatus
 *  - Singleton:      Bakery (central data store)
 *  - Observer:       Bakery and Cart notify GUI panels (listeners) when their data changes
 *  - Custom Icon:    NavIcon implements javax.swing.Icon (icons drawn with Java2D, no files needed)
 */
public class MicroBakeryApp extends JFrame {
    //  THEME
    static final Color C_TEXT   = new Color(0x111111);   // main text
    static final Color C_GREY   = new Color(0x6B5454);   // muted text (readable on white, the page and selected rows)
    static final Color C_DARK   = new Color(0x4E2020);   // headings + strong accents (also the dark end of the gradients)
    static final Color C_ACCENT = new Color(0x794747);   // primary buttons, selection, prices (white text = 7.5:1)
    static final Color C_SOFT   = new Color(0xB9A0A0);   // secondary surfaces: borders, secondary buttons, grid lines
    static final Color C_LIGHT  = new Color(0xF7F7F7);   // page background (also the light text colour on dark surfaces)
    static final Color C_ALERT  = new Color(0xB3261E);   // only used for low-stock warnings
    static final Color C_TINT   = new Color(0xEFE6E6);   // selected-row / selected-option tint
    static final Color C_HOVER  = new Color(0xFBF5F5);   // card hover fill
    static final Font F_TITLE = new Font("Georgia", Font.BOLD, 34);
    static final Font F_H2    = new Font("Georgia", Font.BOLD, 20);static final Font F_BODY  = new Font("SansSerif", Font.PLAIN, 14);
    static final Font F_BOLD  = new Font("SansSerif", Font.BOLD, 14);
    static final Font F_SMALL = new Font("SansSerif", Font.PLAIN, 12);

    // ---- Header contact details: PLACEHOLDERS ONLY. Change these four lines when you have real details. ----
    static final String CONTACT_PHONE  = "+92 XXX XXXXXXX";
    static final String CONTACT_EMAIL  = "hello@crumbandwhisk.com";
    static final String CONTACT_HOURS  = "Mon\u2013Sat | 9 AM\u20138 PM";
    static final String CONTACT_SOCIAL = "Instagram  |  Facebook";

    static String rs(double v)  { return String.format("Rs. %,.0f", v); }
    /** Mixes two colours: t = 0 gives a, t = 1 gives b (used for smooth hover transitions). */
    static Color blend(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
    static String num(double v) { return v == Math.floor(v) ? String.format("%.0f", v) : String.format("%.2f", v); }
    static String cartText(Cart c) { return "Cart: " + c.count() + (c.count() == 1 ? " item" : " items") + "  |  " + rs(c.total()); }
    static boolean isValidCustomerName(String name) {
        return name.matches("[\\p{L}\\p{M}]+(?: +[\\p{L}\\p{M}]+|['\\u2019-][\\p{L}\\p{M}]+)*");
    }
    static final String PHONE_PREFIX = "+92";
    /** Validates a complete, normalized Pakistani mobile number: +92 followed by 3 and 9 more digits. */
    static boolean isValidPakistaniPhone(String phone) {
        return phone.matches("\\+923[0-9]{9}");
    }
    /**
     * Reduces whatever was typed or pasted to the local digits only (max 10), so the +92 country code
     * is never duplicated: a pasted "+92...", "0092...", "92..." or trunk-prefix "0..." is stripped.
     */
    static String normalizeLocalPhone(String raw) {
        String d = raw.replaceAll("[^0-9]", "");
        boolean changed = true;
        while (changed) {
            changed = false;
            if (d.startsWith("0092")) { d = d.substring(4); changed = true; }
            else if (d.startsWith("92")) { d = d.substring(2); changed = true; }
            else if (d.startsWith("0")) { d = d.substring(1); changed = true; }
        }
        return d.length() > 10 ? d.substring(0, 10) : d;
    }

    // =====================================================================
    //  ENUMS
    // =====================================================================
    enum Category {
        CAKES("Cakes"), CUPCAKES("Cupcakes"), BROWNIES("Brownies"), COOKIES("Cookies"),
        DONUTS("Donuts"), PASTRIES("Pastries & Muffins"), BREAD("Bread");
        private final String text;
        Category(String t) { text = t; }
        @Override public String toString() { return text; }
    }

    enum Diet {
        NONE("No preference"), EGGLESS("Eggless"), DAIRY_FREE("Dairy-free"), VEGAN("Vegan"),
        GLUTEN_FREE("Gluten-free"), SUGAR_FREE("Sugar-free");
        private final String text;
        Diet(String t) { text = t; }
        @Override public String toString() { return text; }
    }

    enum PaymentMethod {
        COD("Cash on Delivery"), ONLINE("Online Payment");
        private final String text;
        PaymentMethod(String t) { text = t; }
        @Override public String toString() { return text; }
    }

    enum PaymentStatus {
        PENDING("Pending"), PAID("Paid"), DEMO_PAID("Paid");
        private final String text;
        PaymentStatus(String t) { text = t; }
        @Override public String toString() { return text; }
    }

    enum OrderStatus {
        PLACED("Order Placed"), CONFIRMED("Order Confirmed"), PREPARING("Preparing"),
        READY("Ready for Pickup"), COMPLETED("Completed");
        private final String text;
        OrderStatus(String t) { text = t; }
        OrderStatus next() { OrderStatus[] v = values(); return ordinal() < v.length - 1 ? v[ordinal() + 1] : null; }
        String label(boolean delivery) { return this == READY && delivery ? "Out for Delivery" : text; }
        @Override public String toString() { return text; }
    }

    // =====================================================================
    //  MODEL LAYER
    // =====================================================================
    /** Anything that can say whether it satisfies a dietary preference. */
    interface DietCompatible { boolean supports(Diet d); }

    /** Selectable cake option (flavour, frosting or topping) with a price and the diets it can satisfy. */
    static class CakeOption implements DietCompatible, Serializable {
        private static final long serialVersionUID = 0x1866772d8dd253d4L;
        private final String name; private final double extra; private final Set<Diet> diets;
        CakeOption(String name, double extra, Diet... d) {
            this.name = name; this.extra = extra;
            this.diets = d.length == 0 ? EnumSet.noneOf(Diet.class) : EnumSet.copyOf(Arrays.asList(d));
        }
        String getName() { return name; }
        double getExtra() { return extra; }
        @Override public boolean supports(Diet d) { return d == Diet.NONE || diets.contains(d); }
        @Override public String toString() { return extra > 0 ? name + " (+" + rs(extra) + ")" : name; }
    }

    static class CakeSize implements Serializable {
        private static final long serialVersionUID = 0x1d9e7231c61bbd91L;
        private final String name; private final double price, factor;
        CakeSize(String name, double price, double factor) { this.name = name; this.price = price; this.factor = factor; }
        String getName() { return name; }
        double getPrice() { return price; }
        double getFactor() { return factor; }   // multiplies ingredient usage
        @Override public String toString() { return name + " - " + rs(price); }
    }

    /** Ingredient recipes (per 1 kg cake / per box of the other categories). */
    static class Recipes {
        static Map<String, Double> of(double flour, double sugar, double butter, double eggs, double cocoa, double milk) {
            Map<String, Double> m = new LinkedHashMap<>();
            m.put("Flour", flour); m.put("Sugar", sugar); m.put("Butter", butter);
            m.put("Eggs", eggs); m.put("Cocoa", cocoa); m.put("Milk", milk);
            return m;
        }
        static Map<String, Double> forCategory(Category c) {
            switch (c) {
                case CAKES:    return of(0.60, 0.45, 0.30, 5, 0.05, 0.25);
                case BROWNIES: return of(0.25, 0.30, 0.20, 3, 0.10, 0.05);
                case BREAD:    return of(0.50, 0.02, 0.03, 0, 0.00, 0.10);
                case COOKIES:  return of(0.25, 0.15, 0.15, 2, 0.03, 0.03);
                case DONUTS:   return of(0.30, 0.08, 0.06, 2, 0.00, 0.12);
                case PASTRIES: return of(0.30, 0.10, 0.20, 2, 0.01, 0.08);
                default:       return of(0.30, 0.25, 0.15, 3, 0.03, 0.10);  // cupcakes
            }
        }
    }

    /** Abstract base class for everything that can be sold. */
    static abstract class Product implements DietCompatible, Serializable {
        private static final long serialVersionUID = 0x19aaeb6a30fe35c7L;
        private final String name; private final Category category; private final double price, cost;
        Product(String name, Category category, double price, double cost) {
            this.name = name; this.category = category; this.price = price; this.cost = cost;
        }
        String getName() { return name; }
        Category getCategory() { return category; }
        double getPrice() { return price; }
        double getCost() { return cost; }
        abstract String getDescription();
        abstract Map<String, Double> getRecipe();
    }

    /** Ready-made product from the menu. */
    static class BakeryItem extends Product {
        private static final long serialVersionUID = 0xa2354234f842f9abL;
        private final String description, unit; private final Set<Diet> diets;
        private final Map<String, Double> ownRecipe;     // null = use the category's general recipe (also null in older saved orders)
        BakeryItem(String name, Category cat, String description, String unit, double price, double cost, Diet... d) {
            this(name, cat, description, unit, price, cost, (Map<String, Double>) null, d);
        }
        /** Item with its own ingredient list, used where a dietary claim must match what is actually in the product. */
        BakeryItem(String name, Category cat, String description, String unit, double price, double cost, Map<String, Double> ownRecipe, Diet... d) {
            super(name, cat, price, cost);
            this.ownRecipe = ownRecipe == null ? null : new LinkedHashMap<>(ownRecipe);
            this.description = description; this.unit = unit;
            this.diets = d.length == 0 ? EnumSet.noneOf(Diet.class) : EnumSet.copyOf(Arrays.asList(d));
        }
        String getShortDescription() { return description; }
        String getUnit() { return unit; }
        String getDietSummary() {
            return diets.isEmpty() ? "Standard recipe" : diets.stream().map(Diet::toString).collect(Collectors.joining(", "));
        }
        @Override String getDescription() { return description + " (" + unit + ")"; }
        @Override Map<String, Double> getRecipe() { return ownRecipe != null ? ownRecipe : Recipes.forCategory(getCategory()); }
        @Override public boolean supports(Diet d) { return d == Diet.NONE || diets.contains(d); }
    }

    /** Customer-designed cake: price and ingredient usage are calculated from the chosen options. */
    static class CustomCake extends Product {
        private static final long serialVersionUID = 0x3340c0b6af69ba51L;
        private static final double COST_RATIO = 0.55;
        private final CakeSize size; private final CakeOption flavour, frosting;
        private final List<CakeOption> toppings; private final Diet diet; private final String message;

        CustomCake(CakeSize size, CakeOption flavour, CakeOption frosting, List<CakeOption> toppings, Diet diet, String message) {
            super("Custom " + flavour.getName() + " Cake", Category.CAKES,
                    calc(size, flavour, frosting, toppings), calc(size, flavour, frosting, toppings) * COST_RATIO);
            this.size = size; this.flavour = flavour; this.frosting = frosting;
            this.toppings = new ArrayList<>(toppings); this.diet = diet; this.message = message;
        }
        private static double calc(CakeSize s, CakeOption fl, CakeOption fr, List<CakeOption> tops) {
            double t = s.getPrice() + fl.getExtra() + fr.getExtra();
            for (CakeOption o : tops) t += o.getExtra();
            return t;
        }
        String getBreakdown() {
            StringBuilder sb = new StringBuilder();
            sb.append("Size: ").append(size.getName()).append("  ").append(rs(size.getPrice())).append("\n");
            sb.append("Flavour: ").append(flavour.getName()).append("  +").append(rs(flavour.getExtra())).append("\n");
            sb.append("Frosting: ").append(frosting.getName()).append("  +").append(rs(frosting.getExtra())).append("\n");
            if (toppings.isEmpty()) sb.append("Toppings: none\n");
            for (CakeOption t : toppings) sb.append("Topping: ").append(t.getName()).append("  +").append(rs(t.getExtra())).append("\n");
            sb.append("Dietary preference: ").append(diet).append("\n");
            sb.append("Message: ").append(message.isEmpty() ? "(none)" : "\"" + message + "\"");
            return sb.toString();
        }
        @Override String getDescription() {
            String tops = toppings.isEmpty() ? "None" : toppings.stream().map(CakeOption::getName).collect(Collectors.joining(", "));
            return "Size: " + size.getName() + " | Flavour: " + flavour.getName() + " | Frosting: " + frosting.getName()
                    + " | Toppings: " + tops + " | Diet: " + diet + (message.isEmpty() ? "" : " | Message: \"" + message + "\"");
        }
        @Override Map<String, Double> getRecipe() {
            Map<String, Double> m = new LinkedHashMap<>();
            for (Map.Entry<String, Double> e : Recipes.forCategory(Category.CAKES).entrySet())
                m.put(e.getKey(), e.getValue() * size.getFactor());
            return m;
        }
        @Override public boolean supports(Diet d) { return d == Diet.NONE || d == diet; }
    }

    static class OrderItem implements Serializable {
        private static final long serialVersionUID = 0x52d8b7faa50b85a8L;
        private final Product product; private int qty;
        OrderItem(Product p, int qty) { this.product = p; this.qty = qty; }
        Product getProduct() { return product; }
        int getQty() { return qty; }
        void addQty(int d) { qty += d; }
        double getSubtotal() { return product.getPrice() * qty; }
        double getCost() { return product.getCost() * qty; }
    }

    /**
     * The ONE shared cart used by the Menu, the Custom Cake Builder and Checkout.
     * The cart counter is never stored anywhere: count() always adds up the real items,
     * and every change tells the listeners (nav badge, labels, tables) to refresh.
     */
    static class Cart {
        private final List<OrderItem> items = new ArrayList<>();
        private final List<Runnable> listeners = new ArrayList<>();

        void addListener(Runnable r) { listeners.add(r); }
        private void fire() { for (Runnable r : listeners) r.run(); }

        void add(Product p, int q) {
            if (q <= 0) return;
            boolean merged = false;
            if (!(p instanceof CustomCake))                       // custom cakes are always separate lines
                for (OrderItem i : items) if (i.getProduct() == p) { i.addQty(q); merged = true; break; }
            if (!merged) items.add(new OrderItem(p, q));
            fire();
        }
        void change(int idx, int delta) {
            OrderItem i = items.get(idx); i.addQty(delta);
            if (i.getQty() <= 0) items.remove(idx);
            fire();
        }
        void remove(int idx) { items.remove(idx); fire(); }
        void clear() { items.clear(); fire(); }
        boolean isEmpty() { return items.isEmpty(); }
        int count() { int n = 0; for (OrderItem i : items) n += i.getQty(); return n; }   // total quantity
        double total() { double t = 0; for (OrderItem i : items) t += i.getSubtotal(); return t; }
        /** How many units of this exact product are in the cart right now (0 if none). */
        int qtyOf(Product p) {
            int n = 0;
            for (OrderItem i : items) if (i.getProduct() == p) n += i.getQty();
            return n;
        }
        /** Position of the cart line holding this exact product, or -1 if it is not in the cart. */
        int indexOf(Product p) {
            for (int k = 0; k < items.size(); k++) if (items.get(k).getProduct() == p) return k;
            return -1;
        }
        List<OrderItem> getItems() { return items; }
        List<OrderItem> snapshot() {
            List<OrderItem> l = new ArrayList<>();
            for (OrderItem i : items) l.add(new OrderItem(i.getProduct(), i.getQty()));
            return l;
        }
    }

    static class Order implements Serializable {
        private static final long serialVersionUID = 0x575f70fd639aaccdL;
        static final double DELIVERY_FEE = 150.0;
        private final String id, customerName, phone, address; private final boolean delivery;
        private final double deliveryFee;
        private final PaymentMethod paymentMethod; private PaymentStatus paymentStatus;
        private OrderStatus status = OrderStatus.PLACED;
        private final List<OrderItem> items; private final Date placedAt = new Date();

        Order(String id, String customerName, String phone, boolean delivery, String address, PaymentMethod pm, List<OrderItem> items, double deliveryFee) {
            this.id = id; this.customerName = customerName; this.phone = phone; this.delivery = delivery;
            this.address = address; this.paymentMethod = pm; this.items = items; this.deliveryFee = deliveryFee;
            this.paymentStatus = pm == PaymentMethod.ONLINE ? PaymentStatus.DEMO_PAID : PaymentStatus.PENDING;
        }
        String getId() { return id; }
        String getCustomerName() { return customerName; }
        String getPhone() { return phone; }
        String getAddress() { return address; }
        Date getPlacedAt() { return placedAt; }
        boolean isDelivery() { return delivery; }
        PaymentMethod getPaymentMethod() { return paymentMethod; }
        PaymentStatus getPaymentStatus() { return paymentStatus; }
        void setPaymentStatus(PaymentStatus s) { paymentStatus = s; }
        OrderStatus getStatus() { return status; }
        void setStatus(OrderStatus s) { status = s; }
        List<OrderItem> getItems() { return items; }
        String getStatusText() { return status.label(delivery); }
        double getSubtotal() { double t = 0; for (OrderItem i : items) t += i.getSubtotal(); return t; }
        double getDeliveryFee() { return deliveryFee; }
        double getTotal() { return getSubtotal() + deliveryFee; }
        double getCost() { double t = 0; for (OrderItem i : items) t += i.getCost(); return t; }
        String getSummary() {
            return items.stream().map(i -> i.getQty() + " x " + i.getProduct().getName()).collect(Collectors.joining(", "));
        }
        static boolean isLegacySample(Order order) {
            return "Sample customer".equalsIgnoreCase(order.getCustomerName())
                    && order.getId().matches("MB-H\\d+");
        }
        Map<String, Double> getIngredientNeeds() {
            Map<String, Double> need = new LinkedHashMap<>();
            for (OrderItem it : items)
                for (Map.Entry<String, Double> e : it.getProduct().getRecipe().entrySet())
                    need.merge(e.getKey(), e.getValue() * it.getQty(), Double::sum);
            return need;
        }
        String getFullDetails() {
            StringBuilder sb = new StringBuilder();
            sb.append("Order ").append(id).append("   (").append(new SimpleDateFormat("dd MMM yyyy, hh:mm a").format(placedAt)).append(")\n");
            sb.append("Customer: ").append(customerName).append("   Phone: ").append(phone).append("\n");
            sb.append("Type: ").append(delivery ? "Delivery to " + address : "Pickup from bakery").append("\n");
            sb.append("Payment: ").append(paymentMethod).append(" (").append(paymentStatus).append(")\n");
            sb.append("Status: ").append(getStatusText()).append("\n\nItems:\n");
            for (OrderItem i : items) {
                sb.append("  - ").append(i.getQty()).append(" x ").append(i.getProduct().getName())
                        .append("   ").append(rs(i.getSubtotal())).append("\n");
                if (i.getProduct() instanceof CustomCake)
                    sb.append("      ").append(i.getProduct().getDescription()).append("\n");
            }
            sb.append("\nItems subtotal: ").append(rs(getSubtotal()));
            sb.append("\nDelivery fee: ").append(rs(deliveryFee));
            sb.append("\nTOTAL: ").append(rs(getTotal()));
            return sb.toString();
        }
    }

    /**
     * One stock item. Items counted in pieces (unit "pcs": eggs, packaging...) are DISCRETE and always hold whole numbers;
     * measurable items (kg / L) keep up to 3 decimal places so small recipe amounts are not lost.
     */
    static class Ingredient {
        private static final double MAX_AMOUNT = 1_000_000;      // sanity limit for one restock entry
        private final String name, unit; private final boolean discrete; private double qty; private final double min;
        Ingredient(String name, String unit, double qty, double min) {
            this.name = name; this.unit = unit; this.discrete = "pcs".equalsIgnoreCase(unit);
            this.qty = clean(qty); this.min = clean(min);
        }
        String getName() { return name; }
        String getUnit() { return unit; }
        double getQty() { return qty; }
        double getMin() { return min; }
        boolean isDiscrete() { return discrete; }
        /** Whole pieces for discrete items, otherwise rounded to 3 decimals (removes floating-point drift). */
        private double clean(double v) { return discrete ? Math.floor(v + 1e-9) : Math.round(v * 1000.0) / 1000.0; }
        /** How much stock an order really consumes: whole pieces are rounded UP (2.5 eggs means 3 eggs). */
        double requirement(double need) {
            if (!Double.isFinite(need) || need < 0) throw new IllegalArgumentException("Invalid ingredient requirement for " + name + ".");
            return discrete ? Math.ceil(need - 1e-9) : Math.ceil(need * 1000.0 - 1e-9) / 1000.0;
        }
        /** Validates a purchased amount (restock): finite, positive, sensible size, whole number for discrete items. */
        double normalizeAmount(double amount) {
            if (!Double.isFinite(amount) || amount <= 0) throw new IllegalArgumentException("Please enter a finite positive number.");
            if (amount > MAX_AMOUNT) throw new IllegalArgumentException("That amount is unrealistically large. Enter at most " + num(MAX_AMOUNT) + " " + unit + ".");
            if (discrete) {
                if (Math.abs(amount - Math.rint(amount)) > 1e-9)
                    throw new IllegalArgumentException(name + " is counted in whole " + unit + ". Please enter a whole number.");
                return Math.rint(amount);
            }
            double r = Math.round(amount * 1000.0) / 1000.0;
            if (r <= 0) throw new IllegalArgumentException("That amount is too small. Use at most 3 decimal places (e.g. 0.001 " + unit + ").");
            return r;
        }
        String format(double v) {
            if (discrete) return String.valueOf((long) Math.rint(v));
            return new BigDecimal(v).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        }
        String fmtQty() { return format(qty); }
        String fmtMin() { return format(min); }
        void setQty(double qty) {
            if (!Double.isFinite(qty) || qty < 0)
                throw new IllegalArgumentException("Stock quantity must be finite and non-negative.");
            this.qty = clean(qty);            // an old saved value such as 25.5 eggs becomes 25
        }
        void add(double d) {
            double updatedQty = qty + d;
            if (!Double.isFinite(d) || !Double.isFinite(updatedQty) || updatedQty < 0)
                throw new IllegalArgumentException("Stock quantity must remain finite and non-negative.");
            qty = clean(updatedQty);
        }
        void use(double d) {
            double updatedQty = qty - d;
            if (!Double.isFinite(d) || d < 0 || !Double.isFinite(updatedQty) || updatedQty < 0)
                throw new IllegalArgumentException("Stock quantity must remain finite and non-negative.");
            qty = clean(updatedQty);
        }
        boolean isLow() { return qty < min; }
    }

    // =====================================================================
    //  DATA STORE (Singleton + Observer)
    // =====================================================================
    static class Bakery {

        private static final File DATA_DIR =
                new File(System.getProperty("user.home") + "/CrumbAndWhisk/data");

        private static final File STOCK_FILE = new File(
                DATA_DIR, "inventory.properties"
        );

        private static final File ORDER_FILE = new File(
                DATA_DIR, "orders.ser"
        );

        private static final Bakery INSTANCE = new Bakery();
        static Bakery get() { return INSTANCE; }

        private final List<BakeryItem> menu = new ArrayList<>();
        private final List<Order> orders = new ArrayList<>();
        private final Map<String, Ingredient> stock = new LinkedHashMap<>();
        private final List<Runnable> listeners = new ArrayList<>();
        private final List<CakeSize> sizes = new ArrayList<>();
        private final List<CakeOption> flavours = new ArrayList<>(), frostings = new ArrayList<>(), toppings = new ArrayList<>();
        private int nextId = 1001;

        private Bakery() { loadMenu(); loadOptions(); loadStock(); loadOrders(); }

        private void item(Category c, String n, String desc, String unit, double price, double cost, Diet... d) {
            menu.add(new BakeryItem(n, c, desc, unit, price, cost, d));
        }
        /** Same, but with the product's own ingredient list (kg / L / pcs per unit sold), keys must exist in the stock list. */
        private void item(Category c, String n, String desc, String unit, double price, double cost, Map<String, Double> recipe, Diet... d) {
            menu.add(new BakeryItem(n, c, desc, unit, price, cost, recipe, d));
        }
        private static Map<String, Double> rec(Object... kv) {
            Map<String, Double> m = new LinkedHashMap<>();
            for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], ((Number) kv[i + 1]).doubleValue());
            return m;
        }

        /** MENU DATA: to add or change a product, edit / add one line here. Prices are in Rs. */
        private void loadMenu() {
            Category c = Category.CAKES;
            item(c, "Chocolate Cake", "Moist chocolate sponge with chocolate buttercream", "1 kg", 2000, 1100, Diet.EGGLESS);
            item(c, "Red Velvet Cake", "Red velvet layers with cream cheese frosting", "1 kg", 2400, 1300, Diet.EGGLESS);
            item(c, "Chocolate Truffle Cake", "Dark chocolate sponge with silky truffle ganache", "1 kg", 2200, 1200, Diet.EGGLESS);
            item(c, "Vanilla Cake", "Light vanilla sponge with whipped cream", "1 kg", 1800, 950, Diet.EGGLESS);
            item(c, "Cheesecake", "Baked New York style cheesecake on a biscuit base", "1 kg", 2600, 1450);
            item(c, "Coffee Cake", "Soft coffee sponge with walnut crumble", "1 kg", 2100, 1150);
            item(c, "Strawberry Cake", "Strawberry sponge with fresh strawberry cream", "1 kg", 2300, 1250, Diet.EGGLESS);
            item(c, "Carrot Cake", "Spiced carrot sponge with cream cheese frosting", "1 kg", 2200, 1200);
            item(c, "Flourless Chocolate Cake", "Dense, fudgy chocolate cake made without any flour", "1 kg", 2400, 1300,
                    rec("Cocoa", 0.20, "Sugar", 0.35, "Butter", 0.30, "Eggs", 6, "Vanilla Extract", 0.01), Diet.GLUTEN_FREE);
            item(c, "Sugar-Free Vanilla Cake", "Vanilla sponge with unsweetened cream, no added sugar", "1 kg", 2000, 1100,
                    rec("Cake Flour", 0.55, "Sugar Substitute (Erythritol)", 0.30, "Butter", 0.30, "Eggs", 5, "Milk", 0.25, "Heavy Cream", 0.20, "Vanilla Extract", 0.02), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Chocolate Cake", "Cocoa sponge sweetened without added sugar", "1 kg", 2200, 1200,
                    rec("Cake Flour", 0.50, "Cocoa", 0.10, "Sugar Substitute (Erythritol)", 0.30, "Butter", 0.30, "Eggs", 5, "Milk", 0.25), Diet.SUGAR_FREE);

            item(c, "Vegan Chocolate Cake", "Rich cocoa sponge made with oil, no eggs or dairy", "1 kg", 2300, 1250,
                    rec("Flour", 0.60, "Sugar", 0.45, "Cocoa", 0.10, "Vegetable Oil", 0.15, "Baking Soda", 0.01), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Dairy-Free Carrot Cake", "Spiced carrot sponge made with oil instead of butter, no dairy", "1 kg", 2300, 1250,
                    rec("Flour", 0.55, "Sugar", 0.40, "Vegetable Oil", 0.20, "Eggs", 5, "Ground Cinnamon", 0.01), Diet.DAIRY_FREE);
            item(c, "Lemon Almond Cake", "Moist lemon cake made with almond flour (contains almonds)", "1 kg", 2500, 1350,
                    rec("Almond Flour", 0.50, "Sugar", 0.35, "Butter", 0.25, "Eggs", 6, "Lemon Juice", 0.05), Diet.GLUTEN_FREE);

            // ---- More cakes by dietary preference: every item has its own recipe so stock use matches its dietary label ----
            // Eggless
            item(c, "Eggless Mocha Silk Cake", "Silky coffee-chocolate sponge made without eggs, finished with mocha cream", "1 kg", 2300, 1250,
                    rec("Cake Flour", 0.55, "Sugar", 0.4, "Butter", 0.28, "Milk", 0.25, "Cocoa", 0.08, "Espresso Powder", 0.01, "Baking Powder", 0.02, "Baking Soda", 0.01), Diet.EGGLESS);
            item(c, "Eggless Maple Walnut Cake", "Tender eggless sponge with maple syrup and crunchy walnuts", "1 kg", 2400, 1300,
                    rec("Cake Flour", 0.55, "Sugar", 0.3, "Butter", 0.28, "Milk", 0.25, "Honey or Maple Syrup", 0.1, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.1, "Baking Powder", 0.02, "Baking Soda", 0.01), Diet.EGGLESS);
            // Dairy-free
            item(c, "Dairy-Free Midnight Mocha Cake", "Deep cocoa and coffee sponge made with oil, no dairy", "1 kg", 2400, 1300,
                    rec("Flour", 0.55, "Sugar", 0.42, "Cocoa", 0.1, "Eggs", 5, "Vegetable Oil", 0.2, "Espresso Powder", 0.01, "Baking Powder", 0.02), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Sunshine Lemon Cake", "Bright lemon sponge with a zesty lemon drizzle, no dairy", "1 kg", 2300, 1250,
                    rec("Flour", 0.55, "Sugar", 0.42, "Eggs", 5, "Vegetable Oil", 0.2, "Lemon Juice", 0.06, "Baking Powder", 0.02), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Toasted Coconut Cake", "Light coconut sponge topped with toasted coconut, no dairy", "1 kg", 2400, 1300,
                    rec("Flour", 0.55, "Sugar", 0.42, "Eggs", 5, "Vegetable Oil", 0.2, "Shredded Coconut", 0.12, "Baking Powder", 0.02), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Cinnamon Walnut Cake", "Warm cinnamon sponge studded with walnuts, no dairy", "1 kg", 2400, 1300,
                    rec("Flour", 0.55, "Sugar", 0.4, "Eggs", 5, "Vegetable Oil", 0.2, "Ground Cinnamon", 0.01, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.12, "Baking Powder", 0.02), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Golden Vanilla Cake", "Classic golden vanilla sponge made with oil, no dairy", "1 kg", 2200, 1200,
                    rec("Cake Flour", 0.55, "Sugar", 0.42, "Eggs", 5, "Vegetable Oil", 0.2, "Vanilla Extract", 0.02, "Baking Powder", 0.02), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Festive Fruit Cake", "Spiced sponge loaded with raisins, cranberries and figs, no dairy", "1 kg", 2500, 1350,
                    rec("Flour", 0.5, "Sugar", 0.35, "Eggs", 5, "Vegetable Oil", 0.2, "Dried Fruit (Raisins, Cranberries, Figs)", 0.2, "Ground Cinnamon", 0.01, "Baking Powder", 0.02), Diet.DAIRY_FREE);
            // Vegan (also dairy-free and eggless)
            item(c, "Vegan Lemon Sunshine Cake", "Zesty lemon cake made with oil, no eggs or dairy", "1 kg", 2300, 1250,
                    rec("Flour", 0.6, "Sugar", 0.45, "Vegetable Oil", 0.18, "Lemon Juice", 0.08, "Vanilla Extract", 0.01, "Baking Soda", 0.01, "Baking Powder", 0.02), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Coconut Bliss Cake", "Moist coconut cake with toasted coconut topping, fully plant-based", "1 kg", 2400, 1300,
                    rec("Flour", 0.6, "Sugar", 0.45, "Vegetable Oil", 0.18, "Shredded Coconut", 0.15, "Baking Powder", 0.02, "Baking Soda", 0.01), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Spiced Walnut Cake", "Cinnamon-spiced cake with walnuts, no eggs or dairy", "1 kg", 2400, 1300,
                    rec("Flour", 0.6, "Sugar", 0.42, "Vegetable Oil", 0.18, "Ground Cinnamon", 0.01, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.12, "Baking Powder", 0.02, "Baking Soda", 0.01), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Mocha Velvet Cake", "Rich cocoa and coffee sponge, fully plant-based", "1 kg", 2400, 1300,
                    rec("Flour", 0.58, "Sugar", 0.45, "Cocoa", 0.1, "Vegetable Oil", 0.18, "Espresso Powder", 0.01, "Baking Soda", 0.01, "Baking Powder", 0.02), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Fig and Raisin Cake", "Fruity spiced cake with raisins, cranberries and figs, no eggs or dairy", "1 kg", 2500, 1350,
                    rec("Flour", 0.55, "Sugar", 0.35, "Vegetable Oil", 0.18, "Dried Fruit (Raisins, Cranberries, Figs)", 0.2, "Ground Cinnamon", 0.01, "Baking Soda", 0.01, "Baking Powder", 0.02), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Vanilla Bean Cake", "Soft vanilla sponge made with oil, no eggs or dairy", "1 kg", 2200, 1200,
                    rec("Cake Flour", 0.58, "Sugar", 0.45, "Vegetable Oil", 0.18, "Vanilla Extract", 0.02, "Baking Soda", 0.01, "Baking Powder", 0.02), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Almond Coconut Cake", "Tender cake with almond flour and coconut, plant-based (contains almonds)", "1 kg", 2500, 1350,
                    rec("Flour", 0.4, "Almond Flour", 0.15, "Sugar", 0.42, "Vegetable Oil", 0.18, "Shredded Coconut", 0.12, "Baking Powder", 0.02, "Baking Soda", 0.01), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            // Gluten-free (no wheat flour)
            item(c, "Gluten-Free Coconut Almond Cake", "Moist almond flour cake with coconut, gluten-free (contains almonds)", "1 kg", 2600, 1400,
                    rec("Almond Flour", 0.5, "Shredded Coconut", 0.12, "Sugar", 0.35, "Butter", 0.25, "Eggs", 6, "Vanilla Extract", 0.01, "Baking Powder", 0.02), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Mocha Truffle Cake", "Dense chocolate-coffee cake made with almond flour (contains almonds)", "1 kg", 2700, 1450,
                    rec("Almond Flour", 0.45, "Cocoa", 0.12, "Sugar", 0.35, "Butter", 0.28, "Eggs", 6, "Espresso Powder", 0.01), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Vanilla Bean Cloud Cake", "Light vanilla cake made with almond flour and cornstarch (contains almonds)", "1 kg", 2500, 1350,
                    rec("Almond Flour", 0.4, "Cornstarch", 0.1, "Sugar", 0.38, "Butter", 0.25, "Eggs", 5, "Milk", 0.1, "Vanilla Extract", 0.02, "Baking Powder", 0.02), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Cinnamon Pecan Cake", "Warm spiced cake with crunchy nuts, gluten-free (contains almonds and tree nuts)", "1 kg", 2600, 1400,
                    rec("Almond Flour", 0.5, "Sugar", 0.35, "Butter", 0.25, "Eggs", 6, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.12, "Ground Cinnamon", 0.01, "Baking Powder", 0.02), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Carrot Spice Cake", "Spiced almond flour cake with cream cheese frosting (contains almonds)", "1 kg", 2600, 1400,
                    rec("Almond Flour", 0.5, "Sugar", 0.35, "Eggs", 6, "Vegetable Oil", 0.15, "Ground Cinnamon", 0.01, "Cream Cheese", 0.15, "Baking Powder", 0.02), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Peanut Butter Chocolate Cake", "Chocolate almond flour cake swirled with peanut butter (contains almonds and peanuts)", "1 kg", 2700, 1450,
                    rec("Almond Flour", 0.45, "Cocoa", 0.1, "Sugar", 0.35, "Peanut Butter", 0.15, "Eggs", 5, "Butter", 0.1), Diet.GLUTEN_FREE);
            // Sugar-free (sweetened with erythritol, no added sugar)
            item(c, "Sugar-Free Mocha Velvet Cake", "Cocoa and coffee sponge with no added sugar", "1 kg", 2300, 1250,
                    rec("Cake Flour", 0.5, "Sugar Substitute (Erythritol)", 0.3, "Cocoa", 0.08, "Espresso Powder", 0.01, "Butter", 0.28, "Eggs", 5, "Milk", 0.25, "Baking Powder", 0.02), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Lemon Cream Cake", "Fresh lemon sponge with lightly whipped cream, no added sugar", "1 kg", 2300, 1250,
                    rec("Cake Flour", 0.55, "Sugar Substitute (Erythritol)", 0.3, "Butter", 0.25, "Eggs", 5, "Heavy Cream", 0.2, "Lemon Juice", 0.05, "Baking Powder", 0.02), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Cinnamon Walnut Cake", "Spiced walnut cake with no added sugar", "1 kg", 2400, 1300,
                    rec("Cake Flour", 0.55, "Sugar Substitute (Erythritol)", 0.3, "Butter", 0.28, "Eggs", 5, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.1, "Ground Cinnamon", 0.01, "Baking Powder", 0.02), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Coconut Cream Cake", "Soft coconut sponge with no added sugar", "1 kg", 2300, 1250,
                    rec("Cake Flour", 0.55, "Sugar Substitute (Erythritol)", 0.3, "Butter", 0.28, "Eggs", 5, "Shredded Coconut", 0.12, "Milk", 0.2, "Baking Powder", 0.02), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Spiced Carrot Cake", "Spiced sponge with cream cheese frosting, no added sugar", "1 kg", 2400, 1300,
                    rec("Cake Flour", 0.55, "Sugar Substitute (Erythritol)", 0.3, "Eggs", 5, "Vegetable Oil", 0.15, "Ground Cinnamon", 0.01, "Cream Cheese", 0.15, "Baking Powder", 0.02), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Almond Vanilla Cake", "Vanilla cake with almond flour, no added sugar (contains almonds)", "1 kg", 2400, 1300,
                    rec("Cake Flour", 0.35, "Almond Flour", 0.2, "Sugar Substitute (Erythritol)", 0.3, "Butter", 0.28, "Eggs", 5, "Vanilla Extract", 0.02, "Baking Powder", 0.02), Diet.SUGAR_FREE);

            c = Category.CUPCAKES;
            item(c, "Chocolate Cupcakes", "Chocolate fudge cupcakes with ganache swirl", "box of 6", 1200, 600, Diet.EGGLESS);
            item(c, "Vanilla Cupcakes", "Vanilla cupcakes with buttercream", "box of 6", 1100, 550, Diet.EGGLESS);
            item(c, "Red Velvet Cupcakes", "Red velvet with cream cheese frosting", "box of 6", 1300, 650);
            item(c, "Lemon Vegan Cupcakes", "Zesty lemon cupcakes, plant based", "box of 6", 1250, 640, Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Strawberry Cupcakes", "Vanilla cupcakes with strawberry buttercream", "box of 6", 1250, 620);
            item(c, "Coffee Cupcakes", "Coffee sponge topped with mocha frosting", "box of 6", 1250, 620);
            item(c, "Coconut Cupcakes", "Coconut sponge with toasted coconut frosting", "box of 6", 1300, 650);
            item(c, "Blueberry Cupcakes", "Vanilla sponge with blueberry frosting", "box of 6", 1350, 680);
            item(c, "Almond Flour Cupcakes", "Vanilla cupcakes made with almond flour (contains almonds)", "box of 6", 1400, 720,
                    rec("Almond Flour", 0.25, "Sugar", 0.20, "Butter", 0.15, "Eggs", 4, "Milk", 0.05, "Vanilla Extract", 0.01), Diet.GLUTEN_FREE);
            item(c, "Sugar-Free Chocolate Cupcakes", "Chocolate cupcakes with no added sugar", "box of 6", 1350, 700,
                    rec("Cake Flour", 0.25, "Cocoa", 0.05, "Sugar Substitute (Erythritol)", 0.15, "Butter", 0.12, "Eggs", 3, "Milk", 0.10), Diet.SUGAR_FREE);

            item(c, "Gluten-Free Chocolate Cupcakes", "Chocolate cupcakes made with almond flour (contains almonds)", "box of 6", 1450, 760,
                    rec("Almond Flour", 0.22, "Cocoa", 0.06, "Sugar", 0.18, "Butter", 0.12, "Eggs", 4), Diet.GLUTEN_FREE);
            item(c, "Sugar-Free Vanilla Cupcakes", "Vanilla cupcakes with no added sugar", "box of 6", 1300, 680,
                    rec("Cake Flour", 0.25, "Sugar Substitute (Erythritol)", 0.15, "Butter", 0.12, "Eggs", 3, "Milk", 0.10, "Vanilla Extract", 0.01), Diet.SUGAR_FREE);

            // ---- More cupcakes by dietary preference ----
            // Eggless
            item(c, "Eggless Mocha Cupcakes", "Coffee-chocolate cupcakes made without eggs", "box of 6", 1250, 640,
                    rec("Cake Flour", 0.25, "Sugar", 0.18, "Butter", 0.12, "Milk", 0.12, "Cocoa", 0.04, "Espresso Powder", 0.005, "Baking Powder", 0.01, "Baking Soda", 0.005), Diet.EGGLESS);
            item(c, "Eggless Cinnamon Swirl Cupcakes", "Soft cinnamon cupcakes with a sweet swirl, no eggs", "box of 6", 1150, 590,
                    rec("Cake Flour", 0.25, "Sugar", 0.2, "Butter", 0.12, "Milk", 0.12, "Ground Cinnamon", 0.005, "Baking Powder", 0.01, "Baking Soda", 0.005), Diet.EGGLESS);
            item(c, "Eggless Lemon Cream Cupcakes", "Zesty lemon cupcakes topped with whipped cream, no eggs", "box of 6", 1200, 610,
                    rec("Cake Flour", 0.25, "Sugar", 0.18, "Butter", 0.1, "Milk", 0.08, "Heavy Cream", 0.05, "Lemon Juice", 0.02, "Baking Powder", 0.01, "Baking Soda", 0.005), Diet.EGGLESS);
            item(c, "Eggless Maple Walnut Cupcakes", "Maple-sweetened cupcakes with walnuts, no eggs", "box of 6", 1300, 660,
                    rec("Cake Flour", 0.25, "Sugar", 0.12, "Butter", 0.12, "Milk", 0.12, "Honey or Maple Syrup", 0.04, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.04, "Baking Powder", 0.01, "Baking Soda", 0.005), Diet.EGGLESS);
            item(c, "Eggless Butterscotch Cupcakes", "Buttery caramelised cupcakes with butterscotch frosting, no eggs", "box of 6", 1250, 640,
                    rec("Cake Flour", 0.25, "Sugar", 0.2, "Butter", 0.14, "Milk", 0.1, "Heavy Cream", 0.04, "Vanilla Extract", 0.005, "Baking Powder", 0.01, "Baking Soda", 0.005), Diet.EGGLESS);
            // Dairy-free
            item(c, "Dairy-Free Double Chocolate Cupcakes", "Fudgy cocoa cupcakes made with oil, no dairy", "box of 6", 1300, 650,
                    rec("Flour", 0.25, "Sugar", 0.2, "Cocoa", 0.06, "Eggs", 3, "Vegetable Oil", 0.1, "Baking Powder", 0.01), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Lemon Zest Cupcakes", "Bright lemon cupcakes made with oil, no dairy", "box of 6", 1250, 630,
                    rec("Flour", 0.25, "Sugar", 0.2, "Eggs", 3, "Vegetable Oil", 0.1, "Lemon Juice", 0.03, "Baking Powder", 0.01), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Vanilla Bean Cupcakes", "Classic vanilla cupcakes made with oil, no dairy", "box of 6", 1250, 630,
                    rec("Cake Flour", 0.25, "Sugar", 0.2, "Eggs", 3, "Vegetable Oil", 0.1, "Vanilla Extract", 0.01, "Baking Powder", 0.01), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Cinnamon Spice Cupcakes", "Warm cinnamon cupcakes made with oil, no dairy", "box of 6", 1250, 630,
                    rec("Flour", 0.25, "Sugar", 0.2, "Eggs", 3, "Vegetable Oil", 0.1, "Ground Cinnamon", 0.005, "Baking Powder", 0.01), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Mocha Cupcakes", "Coffee and cocoa cupcakes made with oil, no dairy", "box of 6", 1300, 650,
                    rec("Flour", 0.25, "Sugar", 0.2, "Cocoa", 0.05, "Eggs", 3, "Vegetable Oil", 0.1, "Espresso Powder", 0.005, "Baking Powder", 0.01), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Toasted Coconut Cupcakes", "Coconut cupcakes made with oil, no dairy", "box of 6", 1300, 650,
                    rec("Flour", 0.25, "Sugar", 0.2, "Eggs", 3, "Vegetable Oil", 0.1, "Shredded Coconut", 0.05, "Baking Powder", 0.01), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Peanut Butter Chocolate Cupcakes", "Chocolate cupcakes with peanut butter (contains peanuts), no dairy", "box of 6", 1350, 690,
                    rec("Flour", 0.22, "Sugar", 0.18, "Cocoa", 0.05, "Eggs", 3, "Vegetable Oil", 0.06, "Peanut Butter", 0.06, "Baking Powder", 0.01), Diet.DAIRY_FREE);
            // Vegan (also dairy-free and eggless)
            item(c, "Vegan Double Chocolate Cupcakes", "Rich cocoa cupcakes, no eggs or dairy", "box of 6", 1350, 690,
                    rec("Flour", 0.25, "Sugar", 0.2, "Cocoa", 0.06, "Vegetable Oil", 0.1, "Baking Soda", 0.005, "Baking Powder", 0.01), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Vanilla Bean Cupcakes", "Soft vanilla cupcakes, fully plant-based", "box of 6", 1250, 630,
                    rec("Cake Flour", 0.25, "Sugar", 0.2, "Vegetable Oil", 0.1, "Vanilla Extract", 0.01, "Baking Soda", 0.005, "Baking Powder", 0.01), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Cinnamon Spice Cupcakes", "Warm spiced cupcakes, no eggs or dairy", "box of 6", 1250, 630,
                    rec("Flour", 0.25, "Sugar", 0.2, "Vegetable Oil", 0.1, "Ground Cinnamon", 0.005, "Baking Soda", 0.005, "Baking Powder", 0.01), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Coconut Bliss Cupcakes", "Coconut cupcakes, fully plant-based", "box of 6", 1350, 690,
                    rec("Flour", 0.25, "Sugar", 0.2, "Vegetable Oil", 0.1, "Shredded Coconut", 0.05, "Baking Soda", 0.005, "Baking Powder", 0.01), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Mocha Cupcakes", "Coffee-chocolate cupcakes, no eggs or dairy", "box of 6", 1350, 690,
                    rec("Flour", 0.25, "Sugar", 0.2, "Cocoa", 0.05, "Vegetable Oil", 0.1, "Espresso Powder", 0.005, "Baking Soda", 0.005, "Baking Powder", 0.01), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Raisin Spice Cupcakes", "Spiced cupcakes with raisins and cranberries, no eggs or dairy", "box of 6", 1300, 660,
                    rec("Flour", 0.25, "Sugar", 0.15, "Vegetable Oil", 0.1, "Dried Fruit (Raisins, Cranberries, Figs)", 0.06, "Ground Cinnamon", 0.005, "Baking Soda", 0.005, "Baking Powder", 0.01), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Almond Cupcakes", "Tender almond cupcakes, plant-based (contains almonds)", "box of 6", 1400, 720,
                    rec("Flour", 0.17, "Almond Flour", 0.08, "Sugar", 0.2, "Vegetable Oil", 0.1, "Vanilla Extract", 0.005, "Baking Soda", 0.005, "Baking Powder", 0.01), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            // Gluten-free (no wheat flour)
            item(c, "Gluten-Free Vanilla Bean Cupcakes", "Vanilla cupcakes made with almond flour (contains almonds)", "box of 6", 1450, 750,
                    rec("Almond Flour", 0.22, "Cornstarch", 0.03, "Sugar", 0.18, "Butter", 0.12, "Eggs", 4, "Milk", 0.04, "Vanilla Extract", 0.01, "Baking Powder", 0.01), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Coconut Almond Cupcakes", "Almond flour cupcakes with coconut (contains almonds)", "box of 6", 1500, 780,
                    rec("Almond Flour", 0.22, "Shredded Coconut", 0.05, "Sugar", 0.18, "Butter", 0.12, "Eggs", 4, "Baking Powder", 0.01), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Mocha Cupcakes", "Coffee-chocolate cupcakes made with almond flour (contains almonds)", "box of 6", 1550, 800,
                    rec("Almond Flour", 0.2, "Cocoa", 0.06, "Sugar", 0.18, "Butter", 0.12, "Eggs", 4, "Espresso Powder", 0.005), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Lemon Drizzle Cupcakes", "Lemon cupcakes made with almond flour (contains almonds)", "box of 6", 1500, 780,
                    rec("Almond Flour", 0.22, "Sugar", 0.18, "Butter", 0.12, "Eggs", 4, "Lemon Juice", 0.03, "Baking Powder", 0.01), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Cinnamon Pecan Cupcakes", "Spiced nut cupcakes made with almond flour (contains almonds and tree nuts)", "box of 6", 1550, 800,
                    rec("Almond Flour", 0.22, "Sugar", 0.18, "Butter", 0.12, "Eggs", 4, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.04, "Ground Cinnamon", 0.005, "Baking Powder", 0.01), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Peanut Butter Cupcakes", "Peanut butter cupcakes made with almond flour (contains almonds and peanuts)", "box of 6", 1600, 820,
                    rec("Almond Flour", 0.2, "Peanut Butter", 0.06, "Sugar", 0.16, "Butter", 0.06, "Eggs", 4, "Baking Powder", 0.01), Diet.GLUTEN_FREE);
            // Sugar-free (sweetened with erythritol, no added sugar)
            item(c, "Sugar-Free Mocha Cupcakes", "Coffee-chocolate cupcakes with no added sugar", "box of 6", 1400, 720,
                    rec("Cake Flour", 0.25, "Sugar Substitute (Erythritol)", 0.15, "Cocoa", 0.05, "Espresso Powder", 0.005, "Butter", 0.12, "Eggs", 3, "Milk", 0.1, "Baking Powder", 0.01), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Lemon Cupcakes", "Zesty lemon cupcakes with no added sugar", "box of 6", 1350, 700,
                    rec("Cake Flour", 0.25, "Sugar Substitute (Erythritol)", 0.15, "Butter", 0.12, "Eggs", 3, "Milk", 0.08, "Lemon Juice", 0.03, "Baking Powder", 0.01), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Cinnamon Cupcakes", "Warm cinnamon cupcakes with no added sugar", "box of 6", 1350, 700,
                    rec("Cake Flour", 0.25, "Sugar Substitute (Erythritol)", 0.15, "Butter", 0.12, "Eggs", 3, "Milk", 0.1, "Ground Cinnamon", 0.005, "Baking Powder", 0.01), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Coconut Cupcakes", "Coconut cupcakes with no added sugar", "box of 6", 1400, 720,
                    rec("Cake Flour", 0.25, "Sugar Substitute (Erythritol)", 0.15, "Butter", 0.12, "Eggs", 3, "Shredded Coconut", 0.05, "Milk", 0.08, "Baking Powder", 0.01), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Almond Vanilla Cupcakes", "Vanilla cupcakes with almond flour, no added sugar (contains almonds)", "box of 6", 1450, 750,
                    rec("Cake Flour", 0.15, "Almond Flour", 0.1, "Sugar Substitute (Erythritol)", 0.15, "Butter", 0.12, "Eggs", 3, "Vanilla Extract", 0.01, "Baking Powder", 0.01), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Walnut Spice Cupcakes", "Spiced walnut cupcakes with no added sugar", "box of 6", 1450, 750,
                    rec("Cake Flour", 0.25, "Sugar Substitute (Erythritol)", 0.15, "Butter", 0.12, "Eggs", 3, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.04, "Ground Cinnamon", 0.005, "Baking Powder", 0.01), Diet.SUGAR_FREE);

            c = Category.BROWNIES;
            item(c, "Brownies Box", "Classic fudgy chocolate brownies", "box of 6", 900, 480, Diet.EGGLESS);
            item(c, "Walnut Brownies Box", "Fudgy brownies with walnut crunch", "box of 6", 1000, 540);
            item(c, "Salted Caramel Brownies Box", "Brownies with a salted caramel swirl", "box of 6", 1100, 600);
            item(c, "Almond Brownies Box", "Almond flour brownies", "box of 6", 1200, 650,
                    rec("Almond Flour", 0.20, "Sugar", 0.30, "Cocoa", 0.10, "Eggs", 3, "Vegetable Oil", 0.12), Diet.GLUTEN_FREE, Diet.DAIRY_FREE);
            item(c, "Fudge Brownies Box", "Extra-rich dark chocolate brownies", "box of 6", 950, 500);
            item(c, "Peanut Butter Brownies Box", "Chocolate brownies with peanut butter swirls", "box of 6", 1100, 580);
            item(c, "Mint Chocolate Brownies Box", "Chocolate brownies with a cool mint layer", "box of 6", 1050, 550);
            item(c, "Espresso Brownies Box", "Fudgy brownies with a bold coffee kick", "box of 6", 1050, 560);
            item(c, "Sugar-Free Almond Brownies Box", "Almond flour and cocoa brownies, no added sugar (contains almonds)", "box of 6", 1300, 700,
                    rec("Almond Flour", 0.20, "Cocoa", 0.10, "Sugar Substitute (Erythritol)", 0.20, "Butter", 0.20, "Eggs", 3), Diet.GLUTEN_FREE, Diet.SUGAR_FREE);
            item(c, "Sugar-Free Brownies Box", "Fudgy cocoa brownies with no added sugar", "box of 6", 1100, 600,
                    rec("Flour", 0.25, "Cocoa", 0.10, "Sugar Substitute (Erythritol)", 0.20, "Butter", 0.20, "Eggs", 3), Diet.SUGAR_FREE);
            item(c, "Vegan Chocolate Brownies Box", "Plant-based chocolate brownies made with oil, no eggs or dairy", "box of 6", 1000, 540,
                    rec("Flour", 0.25, "Sugar", 0.30, "Cocoa", 0.10, "Vegetable Oil", 0.15), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);

            // ---- New dietary range ----
            item(c, "Eggless Fudge Lava Brownies Box", "Gooey, fudgy chocolate brownies made without eggs", "box of 6", 1000, 540,
                    rec("Cake Flour", 0.25, "Sugar", 0.3, "Butter", 0.2, "Cocoa", 0.1, "Milk", 0.1, "Baking Powder", 0.01, "Baking Soda", 0.005), Diet.EGGLESS);
            item(c, "Eggless Double Chocolate Chunk Brownies Box", "Eggless cocoa brownies packed with chocolate chunks", "box of 6", 1100, 590,
                    rec("Cake Flour", 0.25, "Sugar", 0.28, "Butter", 0.2, "Cocoa", 0.08, "Chocolate Chips / Chunks", 0.06, "Milk", 0.1, "Baking Powder", 0.01, "Baking Soda", 0.005), Diet.EGGLESS);
            item(c, "Eggless Walnut Crunch Brownies Box", "Chewy eggless brownies topped with crunchy walnuts", "box of 6", 1050, 570,
                    rec("Cake Flour", 0.25, "Sugar", 0.3, "Butter", 0.2, "Cocoa", 0.1, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.06, "Milk", 0.1, "Baking Powder", 0.01, "Baking Soda", 0.005), Diet.EGGLESS);
            item(c, "Eggless Cream Cheese Swirl Brownies Box", "Eggless brownies with a tangy cream cheese ribbon", "box of 6", 1150, 620,
                    rec("Cake Flour", 0.25, "Sugar", 0.3, "Butter", 0.18, "Cocoa", 0.1, "Cream Cheese", 0.08, "Milk", 0.08, "Baking Powder", 0.01, "Baking Soda", 0.005), Diet.EGGLESS);
            item(c, "Eggless Peanut Butter Ripple Brownies Box", "Eggless brownies rippled with peanut butter (contains peanuts)", "box of 6", 1100, 590,
                    rec("Cake Flour", 0.25, "Sugar", 0.28, "Butter", 0.18, "Cocoa", 0.1, "Peanut Butter", 0.06, "Milk", 0.1, "Baking Powder", 0.01, "Baking Soda", 0.005), Diet.EGGLESS);
            item(c, "Eggless Caramel Drizzle Brownies Box", "Eggless fudge brownies finished with buttery caramel", "box of 6", 1100, 590,
                    rec("Cake Flour", 0.25, "Sugar", 0.3, "Butter", 0.2, "Cocoa", 0.1, "Heavy Cream", 0.05, "Fine Salt", 0.005, "Baking Powder", 0.01, "Baking Soda", 0.005), Diet.EGGLESS);
            item(c, "Dairy-Free Triple Cocoa Brownies Box", "Deeply chocolatey brownies made with oil, no dairy", "box of 6", 1100, 590,
                    rec("Flour", 0.25, "Sugar", 0.3, "Cocoa", 0.12, "Eggs", 3, "Vegetable Oil", 0.12, "Baking Powder", 0.01), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Espresso Fudge Brownies Box", "Fudgy coffee-kissed brownies made with oil, no dairy", "box of 6", 1150, 620,
                    rec("Flour", 0.25, "Sugar", 0.3, "Cocoa", 0.1, "Eggs", 3, "Vegetable Oil", 0.12, "Espresso Powder", 0.01, "Baking Powder", 0.01), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Coconut Fudge Brownies Box", "Brownies with toasted coconut, made with oil, no dairy", "box of 6", 1150, 620,
                    rec("Flour", 0.25, "Sugar", 0.3, "Cocoa", 0.1, "Eggs", 3, "Vegetable Oil", 0.12, "Shredded Coconut", 0.05, "Baking Powder", 0.01), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Cinnamon Mocha Brownies Box", "Warm cinnamon and mocha brownies made with oil, no dairy", "box of 6", 1150, 620,
                    rec("Flour", 0.25, "Sugar", 0.3, "Cocoa", 0.1, "Eggs", 3, "Vegetable Oil", 0.12, "Ground Cinnamon", 0.005, "Espresso Powder", 0.005, "Baking Powder", 0.01), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Peanut Butter Swirl Brownies Box", "Chocolate brownies with a peanut butter swirl (contains peanuts), no dairy", "box of 6", 1200, 650,
                    rec("Flour", 0.22, "Sugar", 0.28, "Cocoa", 0.1, "Eggs", 3, "Vegetable Oil", 0.06, "Peanut Butter", 0.06, "Baking Powder", 0.01), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Fruit and Nut Brownies Box", "Fudgy brownies with walnuts and dried fruit, no dairy", "box of 6", 1200, 650,
                    rec("Flour", 0.25, "Sugar", 0.25, "Cocoa", 0.1, "Eggs", 3, "Vegetable Oil", 0.12, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.05, "Dried Fruit (Raisins, Cranberries, Figs)", 0.05, "Baking Powder", 0.01), Diet.DAIRY_FREE);
            item(c, "Vegan Walnut Fudge Brownies Box", "Fudgy walnut brownies, no eggs or dairy", "box of 6", 1100, 590,
                    rec("Flour", 0.25, "Sugar", 0.3, "Cocoa", 0.1, "Vegetable Oil", 0.15, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.06, "Baking Soda", 0.005, "Baking Powder", 0.01), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Peanut Butter Swirl Brownies Box", "Chocolate brownies with a peanut butter swirl, plant-based (contains peanuts)", "box of 6", 1150, 620,
                    rec("Flour", 0.22, "Sugar", 0.28, "Cocoa", 0.1, "Vegetable Oil", 0.08, "Peanut Butter", 0.06, "Baking Soda", 0.005, "Baking Powder", 0.01), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Espresso Fudge Brownies Box", "Fudgy coffee-chocolate brownies, fully plant-based", "box of 6", 1150, 620,
                    rec("Flour", 0.25, "Sugar", 0.3, "Cocoa", 0.1, "Vegetable Oil", 0.15, "Espresso Powder", 0.01, "Baking Soda", 0.005, "Baking Powder", 0.01), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Coconut Fudge Brownies Box", "Chocolate brownies with toasted coconut, no eggs or dairy", "box of 6", 1150, 620,
                    rec("Flour", 0.25, "Sugar", 0.3, "Cocoa", 0.1, "Vegetable Oil", 0.15, "Shredded Coconut", 0.05, "Baking Soda", 0.005, "Baking Powder", 0.01), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Cinnamon Spice Brownies Box", "Warm spiced chocolate brownies, no eggs or dairy", "box of 6", 1100, 590,
                    rec("Flour", 0.25, "Sugar", 0.3, "Cocoa", 0.1, "Vegetable Oil", 0.15, "Ground Cinnamon", 0.008, "Baking Soda", 0.005, "Baking Powder", 0.01), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Cranberry Fudge Brownies Box", "Fudgy brownies with dried cranberries, fully plant-based", "box of 6", 1150, 620,
                    rec("Flour", 0.25, "Sugar", 0.25, "Cocoa", 0.1, "Vegetable Oil", 0.15, "Dried Fruit (Raisins, Cranberries, Figs)", 0.06, "Baking Soda", 0.005, "Baking Powder", 0.01), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Almond Fudge Brownies Box", "Rich brownies with almond flour, plant-based (contains almonds)", "box of 6", 1200, 650,
                    rec("Flour", 0.15, "Almond Flour", 0.08, "Sugar", 0.3, "Cocoa", 0.1, "Vegetable Oil", 0.15, "Baking Soda", 0.005, "Baking Powder", 0.01), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Gluten-Free Dark Fudge Brownies Box", "Dense dark chocolate brownies made with almond flour (contains almonds)", "box of 6", 1300, 700,
                    rec("Almond Flour", 0.18, "Cocoa", 0.12, "Sugar", 0.3, "Butter", 0.2, "Eggs", 3, "Vanilla Extract", 0.005), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Walnut Crunch Brownies Box", "Almond flour brownies with crunchy walnuts (contains almonds and tree nuts)", "box of 6", 1350, 730,
                    rec("Almond Flour", 0.18, "Cocoa", 0.1, "Sugar", 0.28, "Butter", 0.2, "Eggs", 3, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.06), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Peanut Butter Ripple Brownies Box", "Almond flour brownies with a peanut butter ripple (contains almonds and peanuts)", "box of 6", 1350, 730,
                    rec("Almond Flour", 0.16, "Cocoa", 0.1, "Sugar", 0.26, "Butter", 0.1, "Peanut Butter", 0.06, "Eggs", 3), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Espresso Truffle Brownies Box", "Silky coffee-chocolate brownies made with almond flour (contains almonds)", "box of 6", 1350, 730,
                    rec("Almond Flour", 0.18, "Cocoa", 0.12, "Sugar", 0.28, "Butter", 0.2, "Eggs", 3, "Espresso Powder", 0.01), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Coconut Chocolate Brownies Box", "Chocolate brownies with coconut and almond flour (contains almonds)", "box of 6", 1300, 700,
                    rec("Almond Flour", 0.16, "Shredded Coconut", 0.05, "Cocoa", 0.1, "Sugar", 0.28, "Butter", 0.2, "Eggs", 3), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Chocolate Chunk Brownies Box", "Fudgy almond flour brownies with chocolate chunks (contains almonds)", "box of 6", 1400, 760,
                    rec("Almond Flour", 0.18, "Cocoa", 0.08, "Chocolate Chips / Chunks", 0.06, "Sugar", 0.26, "Butter", 0.2, "Eggs", 3), Diet.GLUTEN_FREE);
            item(c, "Sugar-Free Walnut Fudge Brownies Box", "Fudgy cocoa brownies with walnuts and no added sugar", "box of 6", 1250, 670,
                    rec("Cake Flour", 0.25, "Sugar Substitute (Erythritol)", 0.2, "Cocoa", 0.1, "Butter", 0.2, "Eggs", 3, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.06, "Baking Powder", 0.01), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Espresso Fudge Brownies Box", "Coffee-chocolate brownies with no added sugar", "box of 6", 1250, 670,
                    rec("Cake Flour", 0.25, "Sugar Substitute (Erythritol)", 0.2, "Cocoa", 0.1, "Espresso Powder", 0.01, "Butter", 0.2, "Eggs", 3, "Baking Powder", 0.01), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Coconut Fudge Brownies Box", "Chocolate brownies with coconut and no added sugar", "box of 6", 1250, 670,
                    rec("Cake Flour", 0.25, "Sugar Substitute (Erythritol)", 0.2, "Cocoa", 0.1, "Shredded Coconut", 0.05, "Butter", 0.2, "Eggs", 3, "Baking Powder", 0.01), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Cream Cheese Swirl Brownies Box", "Cocoa brownies with a cream cheese ribbon, no added sugar", "box of 6", 1300, 700,
                    rec("Cake Flour", 0.25, "Sugar Substitute (Erythritol)", 0.2, "Cocoa", 0.1, "Cream Cheese", 0.08, "Butter", 0.15, "Eggs", 3, "Baking Powder", 0.01), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Cinnamon Cocoa Brownies Box", "Warm cinnamon chocolate brownies with no added sugar", "box of 6", 1200, 640,
                    rec("Cake Flour", 0.25, "Sugar Substitute (Erythritol)", 0.2, "Cocoa", 0.1, "Ground Cinnamon", 0.008, "Butter", 0.2, "Eggs", 3, "Baking Powder", 0.01), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Almond Mocha Brownies Box", "Mocha brownies with almond flour, no added sugar (contains almonds)", "box of 6", 1300, 700,
                    rec("Cake Flour", 0.12, "Almond Flour", 0.1, "Sugar Substitute (Erythritol)", 0.2, "Cocoa", 0.1, "Espresso Powder", 0.005, "Butter", 0.2, "Eggs", 3), Diet.SUGAR_FREE);
            c = Category.COOKIES;
            item(c, "Chocolate Chip Cookies", "Chewy cookies with milk chocolate chips", "box of 6", 750, 380, Diet.EGGLESS);
            item(c, "Oatmeal Raisin Cookies", "Oats, raisins and cinnamon", "box of 6", 700, 340, Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Double Chocolate Cookies", "Dark chocolate dough, dark chocolate chunks", "box of 6", 800, 400);
            item(c, "Almond Flour Cookies", "Light almond and vanilla cookies", "box of 6", 900, 480,
                    rec("Almond Flour", 0.18, "Sugar Substitute (Erythritol)", 0.10, "Eggs", 2, "Butter", 0.10, "Vanilla Extract", 0.01), Diet.GLUTEN_FREE, Diet.SUGAR_FREE);
            item(c, "Peanut Butter Cookies", "Soft peanut butter cookies with a crisp edge", "box of 6", 800, 400);
            item(c, "Sugar Cookies", "Buttery vanilla cookies with a light sugar crust", "box of 6", 700, 350);
            item(c, "Ginger Molasses Cookies", "Chewy cookies with warming ginger and spice", "box of 6", 750, 380);
            item(c, "Coconut Macaroons", "Golden coconut cookies with a soft centre", "box of 6", 850, 440);
            item(c, "Sugar-Free Coconut Cookies", "Coconut and almond flour cookies, no added sugar (contains almonds)", "box of 6", 950, 500,
                    rec("Almond Flour", 0.15, "Shredded Coconut", 0.10, "Sugar Substitute (Erythritol)", 0.10, "Eggs", 2, "Butter", 0.10), Diet.GLUTEN_FREE, Diet.SUGAR_FREE);

            // ---- New dietary range ----
            item(c, "Eggless Brown Sugar Walnut Cookies", "Buttery brown sugar cookies with walnuts, no eggs", "box of 6", 800, 410,
                    rec("Cake Flour", 0.25, "Brown Sugar", 0.12, "Sugar", 0.05, "Butter", 0.15, "Milk", 0.03, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.04, "Baking Soda", 0.005), Diet.EGGLESS);
            item(c, "Eggless Cinnamon Snickerdoodle Cookies", "Soft cinnamon-sugar cookies, no eggs", "box of 6", 750, 380,
                    rec("Cake Flour", 0.25, "Sugar", 0.15, "Butter", 0.15, "Ground Cinnamon", 0.01, "Cream of Tartar", 0.005, "Milk", 0.03, "Baking Soda", 0.005), Diet.EGGLESS);
            item(c, "Eggless Butter Shortbread Cookies", "Crumbly all-butter shortbread, no eggs", "box of 6", 700, 360,
                    rec("Cake Flour", 0.27, "Sugar", 0.1, "Butter", 0.2, "Vanilla Extract", 0.005), Diet.EGGLESS);
            item(c, "Eggless Lemon Butter Cookies", "Zesty lemon cookies with a buttery crumb, no eggs", "box of 6", 750, 380,
                    rec("Cake Flour", 0.25, "Sugar", 0.14, "Butter", 0.15, "Lemon Juice", 0.02, "Milk", 0.03, "Baking Soda", 0.005), Diet.EGGLESS);
            item(c, "Eggless Mocha Chip Cookies", "Coffee cookies with chocolate chips, no eggs", "box of 6", 850, 430,
                    rec("Cake Flour", 0.25, "Sugar", 0.12, "Butter", 0.15, "Espresso Powder", 0.005, "Chocolate Chips / Chunks", 0.05, "Milk", 0.03, "Baking Soda", 0.005), Diet.EGGLESS);
            item(c, "Eggless Raisin Spice Cookies", "Spiced cookies with raisins and cranberries, no eggs", "box of 6", 800, 410,
                    rec("Cake Flour", 0.25, "Brown Sugar", 0.1, "Butter", 0.15, "Dried Fruit (Raisins, Cranberries, Figs)", 0.05, "Ground Cinnamon", 0.005, "Milk", 0.03, "Baking Soda", 0.005), Diet.EGGLESS);
            item(c, "Dairy-Free Cocoa Crinkle Cookies", "Fudgy crackled cocoa cookies made with oil, no dairy", "box of 6", 800, 410,
                    rec("Flour", 0.25, "Sugar", 0.18, "Cocoa", 0.05, "Eggs", 2, "Vegetable Oil", 0.08, "Baking Powder", 0.005), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Lemon Sugar Cookies", "Bright lemon sugar cookies made with oil, no dairy", "box of 6", 750, 380,
                    rec("Flour", 0.25, "Sugar", 0.16, "Eggs", 2, "Vegetable Oil", 0.09, "Lemon Juice", 0.02, "Baking Powder", 0.005), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Cinnamon Snickerdoodle Cookies", "Soft cinnamon-sugar cookies made with oil, no dairy", "box of 6", 750, 380,
                    rec("Flour", 0.25, "Sugar", 0.16, "Eggs", 2, "Vegetable Oil", 0.09, "Ground Cinnamon", 0.01, "Cream of Tartar", 0.005), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Walnut Brown Sugar Cookies", "Chewy brown sugar cookies with walnuts, no dairy", "box of 6", 850, 430,
                    rec("Flour", 0.25, "Brown Sugar", 0.12, "Sugar", 0.04, "Eggs", 2, "Vegetable Oil", 0.09, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.04, "Baking Powder", 0.005), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Toasted Coconut Cookies", "Crisp coconut cookies made with oil, no dairy", "box of 6", 850, 430,
                    rec("Flour", 0.25, "Sugar", 0.15, "Eggs", 2, "Vegetable Oil", 0.09, "Shredded Coconut", 0.05, "Baking Powder", 0.005), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Mocha Espresso Cookies", "Coffee and cocoa cookies made with oil, no dairy", "box of 6", 850, 430,
                    rec("Flour", 0.25, "Sugar", 0.15, "Cocoa", 0.04, "Eggs", 2, "Vegetable Oil", 0.09, "Espresso Powder", 0.005, "Baking Powder", 0.005), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Fruit and Nut Cookies", "Hearty cookies with dried fruit and nuts, no dairy", "box of 6", 900, 460,
                    rec("Flour", 0.25, "Brown Sugar", 0.1, "Eggs", 2, "Vegetable Oil", 0.09, "Dried Fruit (Raisins, Cranberries, Figs)", 0.04, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.04, "Baking Powder", 0.005), Diet.DAIRY_FREE);
            item(c, "Vegan Cocoa Crinkle Cookies", "Fudgy crackled cocoa cookies, no eggs or dairy", "box of 6", 800, 410,
                    rec("Flour", 0.25, "Sugar", 0.18, "Cocoa", 0.05, "Vegetable Oil", 0.09, "Baking Soda", 0.005, "Baking Powder", 0.005), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Lemon Sugar Cookies", "Bright lemon sugar cookies, fully plant-based", "box of 6", 750, 380,
                    rec("Flour", 0.25, "Sugar", 0.16, "Vegetable Oil", 0.1, "Lemon Juice", 0.02, "Baking Soda", 0.005, "Baking Powder", 0.005), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Cinnamon Snickerdoodle Cookies", "Soft cinnamon-sugar cookies, no eggs or dairy", "box of 6", 750, 380,
                    rec("Flour", 0.25, "Sugar", 0.16, "Vegetable Oil", 0.1, "Ground Cinnamon", 0.01, "Cream of Tartar", 0.005, "Baking Soda", 0.005), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Walnut Brown Sugar Cookies", "Chewy brown sugar cookies with walnuts, fully plant-based", "box of 6", 850, 430,
                    rec("Flour", 0.25, "Brown Sugar", 0.12, "Sugar", 0.04, "Vegetable Oil", 0.1, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.04, "Baking Soda", 0.005), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Toasted Coconut Cookies", "Crisp coconut cookies, no eggs or dairy", "box of 6", 850, 430,
                    rec("Flour", 0.25, "Sugar", 0.15, "Vegetable Oil", 0.1, "Shredded Coconut", 0.05, "Baking Soda", 0.005, "Baking Powder", 0.005), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Mocha Espresso Cookies", "Coffee and cocoa cookies, fully plant-based", "box of 6", 850, 430,
                    rec("Flour", 0.25, "Sugar", 0.15, "Cocoa", 0.04, "Vegetable Oil", 0.1, "Espresso Powder", 0.005, "Baking Soda", 0.005), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Fruit and Nut Cookies", "Hearty cookies with dried fruit and nuts, no eggs or dairy", "box of 6", 900, 460,
                    rec("Flour", 0.25, "Brown Sugar", 0.1, "Vegetable Oil", 0.1, "Dried Fruit (Raisins, Cranberries, Figs)", 0.04, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.04, "Baking Soda", 0.005), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Gluten-Free Chocolate Chunk Almond Cookies", "Chewy almond flour cookies with chocolate chunks (contains almonds)", "box of 6", 1050, 540,
                    rec("Almond Flour", 0.2, "Chocolate Chips / Chunks", 0.05, "Sugar", 0.12, "Butter", 0.12, "Eggs", 2, "Vanilla Extract", 0.005), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Peanut Butter Blossom Cookies", "Soft peanut butter cookies made with almond flour (contains almonds and peanuts)", "box of 6", 1100, 570,
                    rec("Almond Flour", 0.15, "Peanut Butter", 0.08, "Sugar", 0.1, "Eggs", 2, "Butter", 0.04), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Coconut Almond Crisps", "Light, crisp cookies with coconut and almond flour (contains almonds)", "box of 6", 1000, 520,
                    rec("Almond Flour", 0.18, "Shredded Coconut", 0.06, "Sugar", 0.12, "Butter", 0.1, "Eggs", 2), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Cinnamon Pecan Cookies", "Spiced nut cookies made with almond flour (contains almonds and tree nuts)", "box of 6", 1050, 540,
                    rec("Almond Flour", 0.2, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.04, "Ground Cinnamon", 0.005, "Sugar", 0.12, "Butter", 0.12, "Eggs", 2), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Lemon Almond Shortbread", "Buttery lemon shortbread made with almond flour (contains almonds)", "box of 6", 1000, 520,
                    rec("Almond Flour", 0.22, "Cornstarch", 0.03, "Lemon Juice", 0.02, "Sugar", 0.1, "Butter", 0.15), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Mocha Almond Cookies", "Coffee-chocolate cookies made with almond flour (contains almonds)", "box of 6", 1050, 540,
                    rec("Almond Flour", 0.2, "Cocoa", 0.04, "Espresso Powder", 0.005, "Sugar", 0.12, "Butter", 0.12, "Eggs", 2), Diet.GLUTEN_FREE);
            item(c, "Sugar-Free Butter Shortbread Cookies", "Crumbly all-butter shortbread with no added sugar", "box of 6", 900, 460,
                    rec("Cake Flour", 0.27, "Sugar Substitute (Erythritol)", 0.08, "Butter", 0.2, "Vanilla Extract", 0.005), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Cinnamon Snickerdoodle Cookies", "Soft cinnamon cookies with no added sugar", "box of 6", 900, 460,
                    rec("Cake Flour", 0.25, "Sugar Substitute (Erythritol)", 0.1, "Butter", 0.15, "Eggs", 1, "Ground Cinnamon", 0.01, "Cream of Tartar", 0.005), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Lemon Butter Cookies", "Zesty lemon cookies with no added sugar", "box of 6", 900, 460,
                    rec("Cake Flour", 0.25, "Sugar Substitute (Erythritol)", 0.1, "Butter", 0.15, "Eggs", 1, "Lemon Juice", 0.02), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Walnut Crunch Cookies", "Buttery walnut cookies with no added sugar", "box of 6", 950, 490,
                    rec("Cake Flour", 0.25, "Sugar Substitute (Erythritol)", 0.1, "Butter", 0.15, "Eggs", 1, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.04), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Mocha Espresso Cookies", "Coffee and cocoa cookies with no added sugar", "box of 6", 950, 490,
                    rec("Cake Flour", 0.25, "Sugar Substitute (Erythritol)", 0.1, "Cocoa", 0.04, "Espresso Powder", 0.005, "Butter", 0.15, "Eggs", 1), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Cocoa Almond Cookies", "Cocoa cookies with almond flour, no added sugar (contains almonds)", "box of 6", 1000, 520,
                    rec("Cake Flour", 0.12, "Almond Flour", 0.12, "Sugar Substitute (Erythritol)", 0.1, "Cocoa", 0.04, "Butter", 0.12, "Eggs", 2), Diet.SUGAR_FREE);
            c = Category.DONUTS;
            item(c, "Glazed Donuts", "Soft ring donuts with vanilla glaze", "box of 4", 600, 280, Diet.EGGLESS);
            item(c, "Chocolate Dipped Donuts", "Ring donuts dipped in chocolate", "box of 4", 700, 330);
            item(c, "Strawberry Sprinkle Donuts", "Strawberry icing and rainbow sprinkles", "box of 4", 700, 330);
            item(c, "Boston Cream Donuts", "Filled with custard cream", "box of 4", 800, 380);
            item(c, "Cinnamon Sugar Donuts", "Warm-style ring donuts coated in cinnamon sugar", "box of 4", 650, 300);
            item(c, "Maple Glazed Donuts", "Soft donuts finished with maple glaze", "box of 4", 750, 350);
            item(c, "Vanilla Cream Donuts", "Ring donuts filled with vanilla pastry cream", "box of 4", 800, 390);
            item(c, "Old-Fashioned Donuts", "Tender cake donuts with a simple vanilla glaze", "box of 4", 650, 310);

            item(c, "Vegan Vanilla Donuts", "Soft ring donuts made with oil, no eggs or dairy", "box of 4", 700, 340,
                    rec("Flour", 0.30, "Sugar", 0.08, "Vegetable Oil", 0.10, "Powdered Sugar", 0.05), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Gluten-Free Chocolate Donuts", "Chocolate donuts made with almond flour (contains almonds)", "box of 4", 950, 500,
                    rec("Almond Flour", 0.25, "Sugar", 0.08, "Cocoa", 0.03, "Eggs", 2, "Vegetable Oil", 0.06), Diet.GLUTEN_FREE, Diet.DAIRY_FREE);
            item(c, "Sugar-Free Chocolate Donuts", "Cocoa donuts with no added sugar", "box of 4", 850, 450,
                    rec("Flour", 0.30, "Cocoa", 0.03, "Sugar Substitute (Erythritol)", 0.08, "Eggs", 2, "Vegetable Oil", 0.06, "Milk", 0.10), Diet.SUGAR_FREE);

            // ---- New dietary range ----
            item(c, "Eggless Cinnamon Swirl Donuts", "Soft ring donuts with a cinnamon swirl, no eggs", "box of 4", 650, 310,
                    rec("Cake Flour", 0.28, "Sugar", 0.08, "Butter", 0.06, "Milk", 0.1, "Ground Cinnamon", 0.01, "Baking Powder", 0.01), Diet.EGGLESS);
            item(c, "Eggless Chocolate Glazed Donuts", "Fluffy donuts under a glossy cocoa glaze, no eggs", "box of 4", 700, 330,
                    rec("Cake Flour", 0.28, "Sugar", 0.08, "Butter", 0.06, "Milk", 0.1, "Cocoa", 0.03, "Powdered Sugar", 0.04, "Baking Powder", 0.01), Diet.EGGLESS);
            item(c, "Eggless Coconut Dream Donuts", "Tender donuts rolled in shredded coconut, no eggs", "box of 4", 700, 330,
                    rec("Cake Flour", 0.28, "Sugar", 0.08, "Butter", 0.06, "Milk", 0.1, "Shredded Coconut", 0.04, "Baking Powder", 0.01), Diet.EGGLESS);
            item(c, "Eggless Vanilla Bean Donuts", "Classic vanilla donuts with a sweet glaze, no eggs", "box of 4", 650, 310,
                    rec("Cake Flour", 0.28, "Sugar", 0.08, "Butter", 0.06, "Milk", 0.1, "Vanilla Extract", 0.005, "Powdered Sugar", 0.04, "Baking Powder", 0.01), Diet.EGGLESS);
            item(c, "Eggless Maple Pecan Donuts", "Donuts with a maple glaze and crunchy pecans, no eggs", "box of 4", 750, 360,
                    rec("Cake Flour", 0.28, "Sugar", 0.06, "Butter", 0.06, "Milk", 0.1, "Honey or Maple Syrup", 0.04, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.03, "Baking Powder", 0.01), Diet.EGGLESS);
            item(c, "Eggless Lemon Glazed Donuts", "Bright lemon-glazed donuts, no eggs", "box of 4", 700, 330,
                    rec("Cake Flour", 0.28, "Sugar", 0.08, "Butter", 0.06, "Milk", 0.1, "Lemon Juice", 0.02, "Powdered Sugar", 0.04, "Baking Powder", 0.01), Diet.EGGLESS);
            item(c, "Dairy-Free Chocolate Glazed Donuts", "Cocoa-glazed donuts made with oil, no dairy", "box of 4", 750, 360,
                    rec("Flour", 0.28, "Sugar", 0.08, "Eggs", 2, "Vegetable Oil", 0.08, "Cocoa", 0.03, "Powdered Sugar", 0.04, "Baking Powder", 0.01), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Cinnamon Crunch Donuts", "Cinnamon-sugar donuts made with oil, no dairy", "box of 4", 700, 330,
                    rec("Flour", 0.28, "Sugar", 0.1, "Eggs", 2, "Vegetable Oil", 0.08, "Ground Cinnamon", 0.01, "Baking Powder", 0.01), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Toasted Coconut Donuts", "Soft donuts topped with toasted coconut, no dairy", "box of 4", 750, 360,
                    rec("Flour", 0.28, "Sugar", 0.08, "Eggs", 2, "Vegetable Oil", 0.08, "Shredded Coconut", 0.04, "Baking Powder", 0.01), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Lemon Glazed Donuts", "Zesty lemon donuts made with oil, no dairy", "box of 4", 700, 330,
                    rec("Flour", 0.28, "Sugar", 0.08, "Eggs", 2, "Vegetable Oil", 0.08, "Lemon Juice", 0.02, "Powdered Sugar", 0.04, "Baking Powder", 0.01), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Maple Pecan Donuts", "Maple-glazed donuts with pecans, no dairy", "box of 4", 800, 380,
                    rec("Flour", 0.28, "Sugar", 0.06, "Eggs", 2, "Vegetable Oil", 0.08, "Honey or Maple Syrup", 0.04, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.03, "Baking Powder", 0.01), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Espresso Mocha Donuts", "Coffee and cocoa donuts made with oil, no dairy", "box of 4", 800, 380,
                    rec("Flour", 0.28, "Sugar", 0.08, "Eggs", 2, "Vegetable Oil", 0.08, "Cocoa", 0.02, "Espresso Powder", 0.005, "Baking Powder", 0.01), Diet.DAIRY_FREE);
            item(c, "Vegan Chocolate Glazed Donuts", "Cocoa-glazed donuts, no eggs or dairy", "box of 4", 750, 360,
                    rec("Flour", 0.3, "Sugar", 0.1, "Vegetable Oil", 0.09, "Cocoa", 0.03, "Powdered Sugar", 0.04, "Baking Powder", 0.01), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Cinnamon Crunch Donuts", "Cinnamon-sugar donuts, fully plant-based", "box of 4", 700, 330,
                    rec("Flour", 0.3, "Sugar", 0.12, "Vegetable Oil", 0.09, "Ground Cinnamon", 0.01, "Baking Powder", 0.01), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Toasted Coconut Donuts", "Soft donuts topped with toasted coconut, no eggs or dairy", "box of 4", 750, 360,
                    rec("Flour", 0.3, "Sugar", 0.1, "Vegetable Oil", 0.09, "Shredded Coconut", 0.04, "Baking Powder", 0.01), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Lemon Glazed Donuts", "Zesty lemon donuts, fully plant-based", "box of 4", 700, 330,
                    rec("Flour", 0.3, "Sugar", 0.1, "Vegetable Oil", 0.09, "Lemon Juice", 0.02, "Powdered Sugar", 0.04, "Baking Powder", 0.01), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Espresso Mocha Donuts", "Coffee and cocoa donuts, no eggs or dairy", "box of 4", 800, 380,
                    rec("Flour", 0.3, "Sugar", 0.1, "Vegetable Oil", 0.09, "Cocoa", 0.02, "Espresso Powder", 0.005, "Baking Powder", 0.01), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Walnut Crunch Donuts", "Soft donuts topped with crunchy walnuts, fully plant-based", "box of 4", 800, 380,
                    rec("Flour", 0.3, "Sugar", 0.1, "Vegetable Oil", 0.09, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.04, "Baking Powder", 0.01), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Powdered Sugar Donuts", "Pillowy donuts dusted with powdered sugar, no eggs or dairy", "box of 4", 700, 330,
                    rec("Flour", 0.3, "Sugar", 0.08, "Vegetable Oil", 0.09, "Powdered Sugar", 0.05, "Baking Powder", 0.01), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Gluten-Free Cinnamon Sugar Donuts", "Almond flour donuts rolled in cinnamon sugar (contains almonds)", "box of 4", 950, 480,
                    rec("Almond Flour", 0.25, "Sugar", 0.08, "Ground Cinnamon", 0.01, "Eggs", 2, "Butter", 0.05, "Baking Powder", 0.01), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Vanilla Glazed Donuts", "Almond flour donuts with a vanilla glaze (contains almonds)", "box of 4", 950, 480,
                    rec("Almond Flour", 0.25, "Sugar", 0.08, "Powdered Sugar", 0.04, "Vanilla Extract", 0.005, "Eggs", 2, "Butter", 0.05, "Baking Powder", 0.01), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Toasted Coconut Donuts", "Almond flour donuts topped with coconut (contains almonds)", "box of 4", 1000, 510,
                    rec("Almond Flour", 0.25, "Sugar", 0.08, "Shredded Coconut", 0.04, "Eggs", 2, "Butter", 0.05, "Baking Powder", 0.01), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Lemon Glazed Donuts", "Zesty almond flour donuts (contains almonds)", "box of 4", 950, 480,
                    rec("Almond Flour", 0.25, "Sugar", 0.08, "Lemon Juice", 0.02, "Powdered Sugar", 0.04, "Eggs", 2, "Butter", 0.05, "Baking Powder", 0.01), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Espresso Mocha Donuts", "Coffee-chocolate almond flour donuts (contains almonds)", "box of 4", 1000, 510,
                    rec("Almond Flour", 0.25, "Sugar", 0.08, "Cocoa", 0.03, "Espresso Powder", 0.005, "Eggs", 2, "Butter", 0.05, "Baking Powder", 0.01), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Walnut Crunch Donuts", "Almond flour donuts with walnuts (contains almonds and tree nuts)", "box of 4", 1050, 540,
                    rec("Almond Flour", 0.25, "Sugar", 0.08, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.04, "Eggs", 2, "Butter", 0.05, "Baking Powder", 0.01), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Double Chocolate Donuts", "Rich cocoa almond flour donuts with cocoa glaze (contains almonds)", "box of 4", 1050, 540,
                    rec("Almond Flour", 0.22, "Sugar", 0.08, "Cocoa", 0.05, "Powdered Sugar", 0.03, "Eggs", 2, "Butter", 0.05, "Baking Powder", 0.01), Diet.GLUTEN_FREE);
            item(c, "Sugar-Free Cinnamon Donuts", "Cinnamon donuts with no added sugar", "box of 4", 850, 420,
                    rec("Cake Flour", 0.28, "Sugar Substitute (Erythritol)", 0.08, "Butter", 0.06, "Eggs", 2, "Milk", 0.08, "Ground Cinnamon", 0.01, "Baking Powder", 0.01), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Vanilla Bean Donuts", "Classic vanilla donuts with no added sugar", "box of 4", 850, 420,
                    rec("Cake Flour", 0.28, "Sugar Substitute (Erythritol)", 0.08, "Butter", 0.06, "Eggs", 2, "Milk", 0.08, "Vanilla Extract", 0.005, "Baking Powder", 0.01), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Toasted Coconut Donuts", "Coconut donuts with no added sugar", "box of 4", 900, 450,
                    rec("Cake Flour", 0.28, "Sugar Substitute (Erythritol)", 0.08, "Butter", 0.06, "Eggs", 2, "Milk", 0.08, "Shredded Coconut", 0.04, "Baking Powder", 0.01), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Lemon Donuts", "Zesty lemon donuts with no added sugar", "box of 4", 850, 420,
                    rec("Cake Flour", 0.28, "Sugar Substitute (Erythritol)", 0.08, "Butter", 0.06, "Eggs", 2, "Milk", 0.08, "Lemon Juice", 0.02, "Baking Powder", 0.01), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Walnut Crunch Donuts", "Walnut donuts with no added sugar", "box of 4", 900, 450,
                    rec("Cake Flour", 0.28, "Sugar Substitute (Erythritol)", 0.08, "Butter", 0.06, "Eggs", 2, "Milk", 0.08, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.04, "Baking Powder", 0.01), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Espresso Mocha Donuts", "Coffee-cocoa donuts with no added sugar", "box of 4", 900, 450,
                    rec("Cake Flour", 0.28, "Sugar Substitute (Erythritol)", 0.08, "Cocoa", 0.03, "Espresso Powder", 0.005, "Butter", 0.06, "Eggs", 2, "Milk", 0.08, "Baking Powder", 0.01), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Cocoa Almond Donuts", "Cocoa donuts with almond flour, no added sugar (contains almonds)", "box of 4", 950, 480,
                    rec("Cake Flour", 0.14, "Almond Flour", 0.12, "Sugar Substitute (Erythritol)", 0.08, "Cocoa", 0.03, "Butter", 0.06, "Eggs", 2, "Milk", 0.05, "Baking Powder", 0.01), Diet.SUGAR_FREE);
            c = Category.PASTRIES;
            item(c, "Blueberry Muffins", "Soft muffins packed with blueberries", "box of 4", 800, 380, Diet.EGGLESS);
            item(c, "Chocolate Chip Muffins", "Vanilla muffins with chocolate chips", "box of 4", 800, 390);
            item(c, "Banana Walnut Muffins", "Ripe banana and crunchy walnut", "box of 4", 850, 410);
            item(c, "Butter Croissants", "Flaky, golden, all-butter croissants", "box of 4", 900, 430);
            item(c, "Chocolate Eclairs", "Choux pastry with cream and chocolate glaze", "box of 4", 1000, 500);
            item(c, "Assorted Pastries Box", "Chef's mix of danish, puffs and turnovers", "box of 6", 1400, 700);
            item(c, "Apple Turnovers", "Flaky pastry filled with cinnamon apples", "box of 4", 1050, 520);
            item(c, "Almond Danish", "Laminated pastry with almond filling", "box of 4", 1150, 570);
            item(c, "Almond Financiers", "Small buttery almond cakes made with almond flour (contains almonds)", "box of 4", 1100, 560,
                    rec("Almond Flour", 0.15, "Sugar", 0.15, "Butter", 0.15, "Eggs", 4), Diet.GLUTEN_FREE);
            item(c, "Sugar-Free Almond Muffins", "Almond flour muffins with no added sugar (contains almonds)", "box of 4", 1100, 570,
                    rec("Almond Flour", 0.20, "Sugar Substitute (Erythritol)", 0.12, "Butter", 0.10, "Eggs", 3, "Milk", 0.08), Diet.GLUTEN_FREE, Diet.SUGAR_FREE);

            item(c, "Vegan Blueberry Muffins", "Soft blueberry muffins made with oil, no eggs or dairy", "box of 4", 850, 410,
                    rec("Flour", 0.30, "Sugar", 0.10, "Vegetable Oil", 0.10, "Baking Powder", 0.01), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Sugar-Free Cinnamon Muffins", "Cinnamon muffins with no added sugar", "box of 4", 950, 480,
                    rec("Flour", 0.30, "Sugar Substitute (Erythritol)", 0.10, "Eggs", 3, "Milk", 0.08, "Butter", 0.10, "Ground Cinnamon", 0.01), Diet.SUGAR_FREE);

            // ---- New dietary range ----
            item(c, "Eggless Double Chocolate Muffins", "Cocoa muffins with chocolate chips, no eggs", "box of 4", 850, 410,
                    rec("Cake Flour", 0.28, "Sugar", 0.12, "Butter", 0.08, "Milk", 0.12, "Cocoa", 0.04, "Chocolate Chips / Chunks", 0.05, "Baking Powder", 0.012), Diet.EGGLESS);
            item(c, "Eggless Lemon Drizzle Muffins", "Moist lemon muffins with a sweet drizzle, no eggs", "box of 4", 800, 380,
                    rec("Cake Flour", 0.28, "Sugar", 0.12, "Butter", 0.08, "Milk", 0.12, "Lemon Juice", 0.02, "Powdered Sugar", 0.03, "Baking Powder", 0.012), Diet.EGGLESS);
            item(c, "Eggless Cinnamon Streusel Muffins", "Muffins with a buttery cinnamon crumb, no eggs", "box of 4", 800, 380,
                    rec("Cake Flour", 0.28, "Sugar", 0.12, "Butter", 0.1, "Milk", 0.12, "Ground Cinnamon", 0.01, "Brown Sugar", 0.04, "Baking Powder", 0.012), Diet.EGGLESS);
            item(c, "Eggless Walnut Maple Muffins", "Maple-sweet muffins with crunchy walnuts, no eggs", "box of 4", 850, 410,
                    rec("Cake Flour", 0.28, "Sugar", 0.08, "Butter", 0.08, "Milk", 0.12, "Honey or Maple Syrup", 0.04, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.04, "Baking Powder", 0.012), Diet.EGGLESS);
            item(c, "Eggless Butter Scones", "Flaky, golden scones made with butter, no eggs", "box of 4", 900, 430,
                    rec("Cake Flour", 0.3, "Sugar", 0.05, "Butter", 0.12, "Milk", 0.1, "Baking Powder", 0.015), Diet.EGGLESS);
            item(c, "Eggless Mocha Chip Muffins", "Coffee muffins with chocolate chips, no eggs", "box of 4", 850, 410,
                    rec("Cake Flour", 0.28, "Sugar", 0.12, "Butter", 0.08, "Milk", 0.12, "Espresso Powder", 0.005, "Chocolate Chips / Chunks", 0.05, "Baking Powder", 0.012), Diet.EGGLESS);
            item(c, "Dairy-Free Double Chocolate Muffins", "Rich cocoa muffins made with oil, no dairy", "box of 4", 850, 410,
                    rec("Flour", 0.28, "Sugar", 0.14, "Eggs", 2, "Vegetable Oil", 0.08, "Cocoa", 0.05, "Baking Powder", 0.012), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Lemon Drizzle Muffins", "Moist lemon muffins made with oil, no dairy", "box of 4", 800, 380,
                    rec("Flour", 0.28, "Sugar", 0.12, "Eggs", 2, "Vegetable Oil", 0.08, "Lemon Juice", 0.02, "Powdered Sugar", 0.03, "Baking Powder", 0.012), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Cinnamon Streusel Muffins", "Cinnamon muffins with a sugar crumb, no dairy", "box of 4", 800, 380,
                    rec("Flour", 0.28, "Sugar", 0.12, "Eggs", 2, "Vegetable Oil", 0.08, "Ground Cinnamon", 0.01, "Brown Sugar", 0.04, "Baking Powder", 0.012), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Walnut Maple Muffins", "Maple muffins with walnuts, no dairy", "box of 4", 850, 410,
                    rec("Flour", 0.28, "Sugar", 0.08, "Eggs", 2, "Vegetable Oil", 0.08, "Honey or Maple Syrup", 0.04, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.04, "Baking Powder", 0.012), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Coconut Muffins", "Tender muffins with shredded coconut, no dairy", "box of 4", 850, 410,
                    rec("Flour", 0.28, "Sugar", 0.12, "Eggs", 2, "Vegetable Oil", 0.08, "Shredded Coconut", 0.04, "Baking Powder", 0.012), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Cranberry Spice Muffins", "Spiced muffins with dried cranberries, no dairy", "box of 4", 850, 410,
                    rec("Flour", 0.28, "Sugar", 0.1, "Eggs", 2, "Vegetable Oil", 0.08, "Dried Fruit (Raisins, Cranberries, Figs)", 0.04, "Ground Cinnamon", 0.008, "Baking Powder", 0.012), Diet.DAIRY_FREE);
            item(c, "Dairy-Free Mocha Muffins", "Coffee and cocoa muffins made with oil, no dairy", "box of 4", 850, 410,
                    rec("Flour", 0.28, "Sugar", 0.12, "Eggs", 2, "Vegetable Oil", 0.08, "Cocoa", 0.03, "Espresso Powder", 0.005, "Baking Powder", 0.012), Diet.DAIRY_FREE);
            item(c, "Vegan Double Chocolate Muffins", "Rich cocoa muffins, no eggs or dairy", "box of 4", 850, 410,
                    rec("Flour", 0.28, "Sugar", 0.14, "Vegetable Oil", 0.09, "Cocoa", 0.05, "Baking Soda", 0.005, "Baking Powder", 0.012), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Lemon Drizzle Muffins", "Moist lemon muffins, fully plant-based", "box of 4", 800, 380,
                    rec("Flour", 0.28, "Sugar", 0.12, "Vegetable Oil", 0.09, "Lemon Juice", 0.02, "Powdered Sugar", 0.03, "Baking Soda", 0.005, "Baking Powder", 0.012), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Cinnamon Streusel Muffins", "Cinnamon muffins with a sugar crumb, no eggs or dairy", "box of 4", 800, 380,
                    rec("Flour", 0.28, "Sugar", 0.12, "Vegetable Oil", 0.09, "Ground Cinnamon", 0.01, "Brown Sugar", 0.04, "Baking Soda", 0.005, "Baking Powder", 0.012), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Walnut Muffins", "Soft muffins with crunchy walnuts, fully plant-based", "box of 4", 850, 410,
                    rec("Flour", 0.28, "Sugar", 0.12, "Vegetable Oil", 0.09, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.04, "Baking Soda", 0.005, "Baking Powder", 0.012), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Coconut Muffins", "Tender muffins with shredded coconut, no eggs or dairy", "box of 4", 850, 410,
                    rec("Flour", 0.28, "Sugar", 0.12, "Vegetable Oil", 0.09, "Shredded Coconut", 0.04, "Baking Soda", 0.005, "Baking Powder", 0.012), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Cranberry Spice Muffins", "Spiced muffins with dried cranberries, fully plant-based", "box of 4", 850, 410,
                    rec("Flour", 0.28, "Sugar", 0.1, "Vegetable Oil", 0.09, "Dried Fruit (Raisins, Cranberries, Figs)", 0.04, "Ground Cinnamon", 0.008, "Baking Soda", 0.005, "Baking Powder", 0.012), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Mocha Muffins", "Coffee and cocoa muffins, no eggs or dairy", "box of 4", 850, 410,
                    rec("Flour", 0.28, "Sugar", 0.12, "Vegetable Oil", 0.09, "Cocoa", 0.03, "Espresso Powder", 0.005, "Baking Soda", 0.005, "Baking Powder", 0.012), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Gluten-Free Chocolate Almond Muffins", "Cocoa muffins made with almond flour (contains almonds)", "box of 4", 1150, 590,
                    rec("Almond Flour", 0.22, "Sugar", 0.12, "Cocoa", 0.04, "Eggs", 3, "Butter", 0.08, "Baking Powder", 0.012), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Lemon Almond Muffins", "Zesty lemon muffins made with almond flour (contains almonds)", "box of 4", 1100, 560,
                    rec("Almond Flour", 0.22, "Sugar", 0.12, "Lemon Juice", 0.02, "Eggs", 3, "Butter", 0.08, "Baking Powder", 0.012), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Coconut Almond Muffins", "Coconut muffins made with almond flour (contains almonds)", "box of 4", 1150, 590,
                    rec("Almond Flour", 0.2, "Sugar", 0.12, "Shredded Coconut", 0.04, "Eggs", 3, "Butter", 0.08, "Baking Powder", 0.012), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Cinnamon Pecan Muffins", "Spiced nut muffins made with almond flour (contains almonds and tree nuts)", "box of 4", 1200, 620,
                    rec("Almond Flour", 0.22, "Sugar", 0.12, "Ground Cinnamon", 0.008, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.04, "Eggs", 3, "Butter", 0.08, "Baking Powder", 0.012), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Mocha Almond Muffins", "Coffee-chocolate muffins made with almond flour (contains almonds)", "box of 4", 1150, 590,
                    rec("Almond Flour", 0.22, "Sugar", 0.12, "Cocoa", 0.03, "Espresso Powder", 0.005, "Eggs", 3, "Butter", 0.08, "Baking Powder", 0.012), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Cranberry Almond Muffins", "Almond flour muffins with dried cranberries (contains almonds)", "box of 4", 1150, 590,
                    rec("Almond Flour", 0.22, "Sugar", 0.1, "Dried Fruit (Raisins, Cranberries, Figs)", 0.04, "Eggs", 3, "Butter", 0.08, "Baking Powder", 0.012), Diet.GLUTEN_FREE);
            item(c, "Sugar-Free Double Chocolate Muffins", "Cocoa muffins with no added sugar", "box of 4", 1000, 510,
                    rec("Cake Flour", 0.28, "Sugar Substitute (Erythritol)", 0.1, "Cocoa", 0.05, "Butter", 0.08, "Eggs", 2, "Milk", 0.1, "Baking Powder", 0.012), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Lemon Muffins", "Zesty lemon muffins with no added sugar", "box of 4", 950, 480,
                    rec("Cake Flour", 0.28, "Sugar Substitute (Erythritol)", 0.1, "Lemon Juice", 0.02, "Butter", 0.08, "Eggs", 2, "Milk", 0.1, "Baking Powder", 0.012), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Walnut Muffins", "Soft walnut muffins with no added sugar", "box of 4", 1000, 510,
                    rec("Cake Flour", 0.28, "Sugar Substitute (Erythritol)", 0.1, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.04, "Butter", 0.08, "Eggs", 2, "Milk", 0.1, "Baking Powder", 0.012), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Coconut Muffins", "Coconut muffins with no added sugar", "box of 4", 1000, 510,
                    rec("Cake Flour", 0.28, "Sugar Substitute (Erythritol)", 0.1, "Shredded Coconut", 0.04, "Butter", 0.08, "Eggs", 2, "Milk", 0.1, "Baking Powder", 0.012), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Espresso Muffins", "Coffee muffins with no added sugar", "box of 4", 1000, 510,
                    rec("Cake Flour", 0.28, "Sugar Substitute (Erythritol)", 0.1, "Espresso Powder", 0.008, "Butter", 0.08, "Eggs", 2, "Milk", 0.1, "Baking Powder", 0.012), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Butter Scones", "Flaky butter scones with no added sugar", "box of 4", 950, 480,
                    rec("Cake Flour", 0.3, "Sugar Substitute (Erythritol)", 0.04, "Butter", 0.12, "Eggs", 1, "Milk", 0.08, "Baking Powder", 0.015), Diet.SUGAR_FREE);
            c = Category.BREAD;
            item(c, "Sourdough Loaf", "Slow fermented classic sourdough", "1 loaf", 650, 300, Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Multigrain Loaf", "Seeds and whole grains", "1 loaf", 550, 250, Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Garlic Butter Loaf", "Garlic and herb butter", "1 loaf", 500, 230, Diet.EGGLESS);
            item(c, "Cinnamon Raisin Loaf", "Cinnamon swirl with raisins", "1 loaf", 600, 280, Diet.EGGLESS);
            item(c, "Whole Wheat Loaf", "Soft whole wheat bread for everyday sandwiches", "1 loaf", 550, 260, Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Brioche Loaf", "Rich, soft bread made with butter and eggs", "1 loaf", 750, 380);
            item(c, "Rosemary Focaccia", "Olive oil bread topped with rosemary", "1 loaf", 700, 340, Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Rye Loaf", "Hearty rye bread with a deep, earthy flavour", "1 loaf", 700, 350, Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Sugar-Free Whole Wheat Loaf", "Everyday whole wheat bread with no added sugar", "1 loaf", 650, 320,
                    rec("Bread Flour", 0.50, "Yeast (Instant or Active Dry)", 0.01, "Fine Salt", 0.01, "Vegetable Oil", 0.03), Diet.SUGAR_FREE, Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Almond Flour Loaf", "Dense, gluten-free loaf made with almond flour (contains almonds)", "1 loaf", 850, 430,
                    rec("Almond Flour", 0.30, "Eggs", 3, "Vegetable Oil", 0.05, "Baking Powder", 0.01), Diet.GLUTEN_FREE, Diet.DAIRY_FREE);
            // ---- New dietary range ----
            item(c, "Dairy-Free Golden Brioche Loaf", "Soft, rich egg bread made with oil, no dairy", "1 loaf", 700, 340,
                    rec("Bread Flour", 0.45, "Sugar", 0.04, "Eggs", 2, "Vegetable Oil", 0.05, "Yeast (Instant or Active Dry)", 0.01, "Fine Salt", 0.01), Diet.DAIRY_FREE);
            item(c, "Vegan Olive Oil Ciabatta Loaf", "Airy, crusty ciabatta made with olive-style oil, no eggs or dairy", "1 loaf", 650, 300,
                    rec("Bread Flour", 0.5, "Vegetable Oil", 0.05, "Yeast (Instant or Active Dry)", 0.01, "Fine Salt", 0.01), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Vegan Walnut Sandwich Loaf", "Soft sandwich loaf with walnuts, fully plant-based", "1 loaf", 650, 300,
                    rec("Bread Flour", 0.5, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.03, "Vegetable Oil", 0.03, "Yeast (Instant or Active Dry)", 0.01, "Fine Salt", 0.01), Diet.VEGAN, Diet.DAIRY_FREE, Diet.EGGLESS);
            item(c, "Gluten-Free Nutty Almond Loaf", "Dense almond flour loaf with nuts (contains almonds and tree nuts)", "1 loaf", 900, 450,
                    rec("Almond Flour", 0.3, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.03, "Eggs", 3, "Vegetable Oil", 0.05, "Baking Powder", 0.01), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Cinnamon Walnut Loaf", "Almond flour loaf with cinnamon and walnuts (contains almonds and tree nuts)", "1 loaf", 950, 480,
                    rec("Almond Flour", 0.3, "Ground Cinnamon", 0.008, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.04, "Eggs", 3, "Vegetable Oil", 0.05, "Baking Powder", 0.01), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Coconut Almond Loaf", "Soft coconut loaf made with almond flour (contains almonds)", "1 loaf", 900, 450,
                    rec("Almond Flour", 0.28, "Shredded Coconut", 0.04, "Eggs", 3, "Vegetable Oil", 0.05, "Baking Powder", 0.01), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Lemon Almond Loaf", "Light lemon loaf made with almond flour (contains almonds)", "1 loaf", 900, 450,
                    rec("Almond Flour", 0.3, "Lemon Juice", 0.02, "Eggs", 3, "Vegetable Oil", 0.05, "Baking Powder", 0.01), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Savoury Sandwich Loaf", "Savoury almond flour sandwich loaf (contains almonds)", "1 loaf", 900, 450,
                    rec("Almond Flour", 0.25, "Cornstarch", 0.05, "Eggs", 3, "Vegetable Oil", 0.05, "Baking Powder", 0.01, "Fine Salt", 0.01), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Cocoa Almond Loaf", "Cocoa-kissed loaf made with almond flour (contains almonds)", "1 loaf", 950, 480,
                    rec("Almond Flour", 0.28, "Cocoa", 0.03, "Eggs", 3, "Vegetable Oil", 0.05, "Baking Powder", 0.01), Diet.GLUTEN_FREE);
            item(c, "Gluten-Free Cranberry Pecan Loaf", "Almond flour loaf with dried cranberries and pecans (contains almonds and tree nuts)", "1 loaf", 1000, 510,
                    rec("Almond Flour", 0.28, "Dried Fruit (Raisins, Cranberries, Figs)", 0.04, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.03, "Eggs", 3, "Vegetable Oil", 0.05, "Baking Powder", 0.01), Diet.GLUTEN_FREE);
            item(c, "Sugar-Free Country Loaf", "Rustic walnut bread with no added sugar", "1 loaf", 600, 290,
                    rec("Bread Flour", 0.5, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.03, "Vegetable Oil", 0.03, "Yeast (Instant or Active Dry)", 0.01, "Fine Salt", 0.01), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Sandwich Loaf", "Hearty sandwich bread with no added sugar", "1 loaf", 650, 310,
                    rec("Bread Flour", 0.5, "Vegetable Oil", 0.03, "Yeast (Instant or Active Dry)", 0.01, "Fine Salt", 0.01), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Walnut Loaf", "Crusty bread with crunchy walnuts and no added sugar", "1 loaf", 700, 340,
                    rec("Bread Flour", 0.5, "Chopped Nuts (Walnuts, Pecans, Pistachios)", 0.05, "Vegetable Oil", 0.03, "Yeast (Instant or Active Dry)", 0.01, "Fine Salt", 0.01), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Garlic Butter Loaf", "Savoury butter bread with no added sugar", "1 loaf", 650, 310,
                    rec("Bread Flour", 0.5, "Butter", 0.05, "Yeast (Instant or Active Dry)", 0.01, "Fine Salt", 0.01), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Cinnamon Swirl Loaf", "Cinnamon swirl bread sweetened with erythritol", "1 loaf", 700, 340,
                    rec("Bread Flour", 0.5, "Sugar Substitute (Erythritol)", 0.04, "Ground Cinnamon", 0.01, "Butter", 0.04, "Yeast (Instant or Active Dry)", 0.01, "Fine Salt", 0.01), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Olive Oil Focaccia", "Soft olive-oil focaccia with no added sugar", "1 loaf", 700, 340,
                    rec("Bread Flour", 0.5, "Vegetable Oil", 0.06, "Yeast (Instant or Active Dry)", 0.01, "Fine Salt", 0.01), Diet.SUGAR_FREE);
            item(c, "Sugar-Free Milk Loaf", "Soft, fluffy milk bread with no added sugar", "1 loaf", 650, 310,
                    rec("Bread Flour", 0.5, "Milk", 0.15, "Butter", 0.04, "Yeast (Instant or Active Dry)", 0.01, "Fine Salt", 0.01), Diet.SUGAR_FREE);
        }

        private void loadOptions() {
            Diet[] all = Diet.values();
            sizes.add(new CakeSize("Small", 1400, 0.5));
            sizes.add(new CakeSize("Medium", 2400, 1.0));
            sizes.add(new CakeSize("Large", 3400, 1.5));
            sizes.add(new CakeSize("Party", 4400, 2.0));

            flavours.add(new CakeOption("Vanilla", 0, all));
            flavours.add(new CakeOption("Chocolate", 0, Diet.EGGLESS, Diet.DAIRY_FREE, Diet.VEGAN, Diet.GLUTEN_FREE));
            flavours.add(new CakeOption("Red Velvet", 300, Diet.EGGLESS));
            flavours.add(new CakeOption("Butterscotch", 250, Diet.EGGLESS));
            flavours.add(new CakeOption("Coffee", 200, Diet.EGGLESS, Diet.SUGAR_FREE));
            flavours.add(new CakeOption("Lemon", 150, Diet.EGGLESS, Diet.DAIRY_FREE, Diet.VEGAN, Diet.SUGAR_FREE));

            frostings.add(new CakeOption("Buttercream", 0, Diet.EGGLESS, Diet.GLUTEN_FREE));
            frostings.add(new CakeOption("Whipped Cream", 200, Diet.EGGLESS, Diet.GLUTEN_FREE));
            frostings.add(new CakeOption("Cream Cheese", 350, Diet.EGGLESS, Diet.GLUTEN_FREE));
            frostings.add(new CakeOption("Chocolate Ganache", 400, Diet.EGGLESS, Diet.GLUTEN_FREE));
            frostings.add(new CakeOption("Coconut Cream", 300, all));
            frostings.add(new CakeOption("Fondant", 500, Diet.EGGLESS, Diet.DAIRY_FREE, Diet.VEGAN));
            frostings.add(new CakeOption("Stevia Glaze", 250, all));

            toppings.add(new CakeOption("Fresh Fruits", 150, all));
            toppings.add(new CakeOption("Chocolate Chips", 120, Diet.EGGLESS, Diet.GLUTEN_FREE));
            toppings.add(new CakeOption("Sprinkles", 80, Diet.EGGLESS, Diet.DAIRY_FREE, Diet.VEGAN));
            toppings.add(new CakeOption("Crushed Nuts", 180, all));
            toppings.add(new CakeOption("Caramel Drizzle", 140, Diet.EGGLESS, Diet.GLUTEN_FREE));
            toppings.add(new CakeOption("Edible Flowers", 200, all));
        }

        private void loadStock() {
            // Keep existing recipe keys: Flour, Sugar, Butter and Cocoa cover the matching requested staples.
            addStock("Flour", "kg", 48, 17);
            addStock("Sugar", "kg", 80, 28);
            addStock("Butter", "kg", 3, 12);      // starts low so the alert is visible in the demo
            addStock("Eggs", "pcs", 360, 126);
            addStock("Cocoa", "kg", 12, 6);
            addStock("Milk", "L", 60, 21);
            addStock("Brown Sugar", "kg", 16, 5.5);
            addStock("Vanilla Extract", "L", 4, 1.5);
            addStock("Fine Salt", "kg", 8, 3);
            addStock("Baking Powder", "kg", 4, 1.5);
            addStock("Baking Soda", "kg", 4, 1.5);
            addStock("Bread Flour", "kg", 40, 14);
            addStock("Cake Flour", "kg", 20, 7);
            addStock("Vegetable Oil", "L", 20, 7);
            addStock("Buttermilk", "L", 12, 4);
            addStock("Heavy Cream", "L", 12, 4);
            addStock("Cream Cheese", "kg", 8, 3);
            addStock("Powdered Sugar", "kg", 16, 5.5);
            addStock("Honey or Maple Syrup", "L", 8, 3);
            addStock("Chocolate Chips / Chunks", "kg", 12, 4);
            addStock("Yeast (Instant or Active Dry)", "kg", 4, 1.5);
            addStock("Cornstarch", "kg", 4, 1.5);
            addStock("Cream of Tartar", "kg", 2, 0.7);
            addStock("Ground Cinnamon", "kg", 4, 1.5);
            addStock("Espresso Powder", "kg", 2, 0.7);
            addStock("Dried Fruit (Raisins, Cranberries, Figs)", "kg", 12, 4);
            addStock("Chopped Nuts (Walnuts, Pecans, Pistachios)", "kg", 12, 4);
            addStock("Shredded Coconut", "kg", 8, 3);
            addStock("Gelatin Powder / Sheets", "kg", 2, 0.7);
            addStock("Malted Milk Powder", "kg", 4, 1.5);
            addStock("Coarse Sanding Sugar", "kg", 8, 3);
            addStock("Almond Flour", "kg", 12, 4);
            addStock("Sugar Substitute (Erythritol)", "kg", 12, 4);
            addStock("Food Colouring (bottles)", "pcs", 48, 17);
            addStock("Lemon Juice", "L", 8, 3);
            addStock("Peanut Butter", "kg", 12, 4);
            addStock("Cupcake Liners", "pcs", 1200, 420);
            addStock("Cake Boxes", "pcs", 160, 56);
            // More bakery ingredients. Quantity and minimum level start at 0: the administrator enters the real stock with
            // "Restock selected ingredient". (Existing items already cover: Flour = all-purpose, Sugar = white sugar,
            // Powdered Sugar = icing sugar, Heavy Cream, Cocoa = cocoa powder, Cornstarch = cornflour, Fine Salt, etc.)
            // Quantity starts at 0 (enter the real stock with "Restock selected ingredient"); each item has a minimum level, so it shows
            // as LOW STOCK until it has been restocked above that level.
            Object[][] more = {
                    {"Whole Wheat Flour", "kg", 4}, {"Honey", "kg", 1}, {"Margarine", "kg", 2}, {"Dark Chocolate", "kg", 2},
                    {"Milk Chocolate", "kg", 2}, {"White Chocolate", "kg", 2}, {"Walnuts", "kg", 2}, {"Almonds", "kg", 2},
                    {"Pistachios", "kg", 1}, {"Raisins", "kg", 1}, {"Dates", "kg", 1}, {"Dried Cranberries", "kg", 1},
                    {"Sprinkles", "kg", 1}, {"Edible Pearls", "kg", 0.5},
                    {"Condensed Milk", "L", 2}, {"Strawberry Essence", "L", 0.2}, {"Almond Essence", "L", 0.2},
                    {"Coffee Essence", "L", 0.2}, {"Lemon Essence", "L", 0.2}};
            for (Object[] m : more) addStock((String) m[0], (String) m[1], 0, ((Number) m[2]).doubleValue());
            // Further bakery ingredients (requested later). Honey, Baking Soda, Baking Powder, Dark / Milk / White Chocolate already exist above;
            // Chocolate Chips = "Chocolate Chips / Chunks", Unsweetened Cocoa Powder = "Cocoa" and Salt = "Fine Salt", so those are not added twice.
            addStock("Agave Nectar", "L", 8, 3);
            addStock("Active Dry Yeast", "kg", 2, 0.7);
            addStock("Instant Yeast", "kg", 2, 0.7);
            addStock("Fresh Yeast", "kg", 2, 0.7);
            addStock("Ground Chocolate", "kg", 4, 1.5);
            addStock("Cream", "L", 12, 4);
            addStock("Creamed Butter", "kg", 8, 3);
            addStock("Unsalted Butter", "kg", 20, 7);
            addStock("Laminated Dough", "kg", 12, 4);
            addStock("Melted Butter", "kg", 4, 1.5);
            addStock("Self-Rising Flour", "kg", 20, 7);
            addStock("Whipped Egg Whites", "L", 4, 1.5);
            addStock("Club Soda", "pcs", 96, 34);
            addStock("Vanilla Paste", "kg", 2, 0.7);
            loadSavedStock();
        }
        private void addStock(String n, String u, double q, double min) {
            stock.putIfAbsent(n, new Ingredient(n, u, q, min));
        }

        private void loadSavedStock() {
            if (!STOCK_FILE.isFile()) return;
            Properties saved = new Properties();
            try (FileInputStream input = new FileInputStream(STOCK_FILE)) {
                saved.load(input);
            } catch (IOException ex) {
                throw new IllegalStateException("Could not load inventory from " + STOCK_FILE + ": " + ex.getMessage(), ex);
            }
            for (Ingredient ingredient : stock.values()) {
                String value = saved.getProperty(ingredient.getName());
                if (value == null) continue;
                try {
                    double qty = Double.parseDouble(value);
                    if (Double.isNaN(qty) || Double.isInfinite(qty) || qty < 0)
                        throw new NumberFormatException("quantity must be finite and non-negative");
                    ingredient.setQty(qty);
                } catch (NumberFormatException ex) {
                    throw new IllegalStateException("Invalid saved quantity for " + ingredient.getName() + " in " + STOCK_FILE, ex);
                }
            }
        }

        private void loadOrders() {
            if (!ORDER_FILE.isFile()) return;
            List<Order> loaded = new ArrayList<>();
            try (ObjectInputStream input = new ObjectInputStream(new FileInputStream(ORDER_FILE))) {
                Object saved = input.readObject();
                if (!(saved instanceof List<?>))
                    throw new IOException("The saved order data is not a list.");
                for (Object entry : (List<?>) saved) {
                    if (!(entry instanceof Order))
                        throw new IOException("The saved order data contains an invalid record.");
                    loaded.add((Order) entry);
                }
            } catch (IOException | ClassNotFoundException ex) {
                throw new IllegalStateException("Could not load customer orders from " + ORDER_FILE + ": " + ex.getMessage(), ex);
            }
            orders.addAll(loaded);
            for (Order order : orders) {
                String id = order.getId();
                if (id.startsWith("MB-")) {
                    try {
                        int savedId = Integer.parseInt(id.substring(3).split("-")[0]);       // "MB-1005" -> 1005 (also copes with older IDs that carry a suffix)
                        if (savedId < Integer.MAX_VALUE) nextId = Math.max(nextId, savedId + 1);
                    } catch (NumberFormatException ignored) {
                        // Older order IDs may use a non-numeric suffix.
                    }
                }
            }
        }

        private void saveOrders() throws IOException {
            File parent = ORDER_FILE.getParentFile();
            Files.createDirectories(parent.toPath());
            File temporary = new File(parent, ORDER_FILE.getName() + ".tmp");
            try {
                try (ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(temporary))) {
                    output.writeObject(new ArrayList<>(orders));
                }
                try {
                    Files.move(temporary.toPath(), ORDER_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(temporary.toPath(), ORDER_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary.toPath());
            }
        }

        private void saveStock() throws IOException {
            File parent = STOCK_FILE.getParentFile();
            Files.createDirectories(parent.toPath());

            Properties saved = new Properties();

            for (Ingredient ingredient : stock.values()) {
                saved.setProperty(ingredient.getName(), Double.toString(ingredient.getQty()));
            }

            File temporary = new File(parent, STOCK_FILE.getName() + ".tmp");

            try {
                try (FileOutputStream output = new FileOutputStream(temporary)) {
                    saved.store(output, "Crumb & Whisk inventory quantities");
                }

                try {
                    Files.move(temporary.toPath(), STOCK_FILE.toPath(),
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(temporary.toPath(), STOCK_FILE.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary.toPath());
            }
        }

        // ---- observer support ----
        void addListener(Runnable r) { listeners.add(r); }
        private void fire() { for (Runnable r : listeners) r.run(); }

        // ---- queries ----
        List<BakeryItem> getMenu() { return menu; }
        List<Order> getOrders() { return orders; }
        List<CakeSize> getSizes() { return sizes; }
        List<CakeOption> getFlavours() { return flavours; }
        List<CakeOption> getFrostings() { return frostings; }
        List<CakeOption> getToppings() { return toppings; }
        Collection<Ingredient> getStock() { return stock.values(); }
        Ingredient getIngredient(String name) { return stock.get(name); }
        List<Ingredient> getLowStock() {
            return stock.values().stream().filter(Ingredient::isLow).collect(Collectors.toList());
        }
        Order find(String id) {
            for (Order o : orders) if (o.getId().equalsIgnoreCase(id)) return o;
            return null;
        }
        Map<String, Integer> unitsSold() {
            Map<String, Integer> m = new LinkedHashMap<>();
            for (BakeryItem b : menu) m.put(b.getName(), 0);
            for (Order o : orders) {
                if (Order.isLegacySample(o)) continue;
                for (OrderItem i : o.getItems())
                    if (m.containsKey(i.getProduct().getName()))       // menu products only: custom cakes are not menu items
                        m.merge(i.getProduct().getName(), i.getQty(), Integer::sum);
            }
            return m;
        }
        /** Units of customer-designed cakes sold (reported separately from the menu ranking). */
        int customCakesSold() {
            int n = 0;
            for (Order o : orders) {
                if (Order.isLegacySample(o)) continue;
                for (OrderItem i : o.getItems()) if (i.getProduct() instanceof CustomCake) n += i.getQty();
            }
            return n;
        }

        // ---- actions ----
        Order placeOrder(String name, String phone, boolean delivery, String addr, PaymentMethod pm, List<OrderItem> items) {
            if (!delivery && pm == PaymentMethod.COD)
                throw new IllegalStateException("Cash on Delivery is only available for delivery orders.");
            while (find("MB-" + nextId) != null) nextId++;                  // never reuse an ID that already exists
            Order o = new Order("MB-" + nextId++, name, phone, delivery, addr, pm, items,
                    delivery ? Order.DELIVERY_FEE : 0);
            orders.add(o);
            try {
                saveOrders();
            } catch (IOException ex) {
                orders.remove(o);
                nextId--;
                throw new IllegalStateException("Could not save customer order to " + ORDER_FILE + ": " + ex.getMessage(), ex);
            }
            fire(); return o;
        }
        /** The administrator confirms an order: checks ingredient availability, then deducts stock. Returns error text or null. */
        String confirmOrder(Order o) {
            Map<String, Double> need = new LinkedHashMap<>();       // what the order really consumes (whole pieces rounded up)
            for (Map.Entry<String, Double> raw : o.getIngredientNeeds().entrySet()) {
                Ingredient ing = stock.get(raw.getKey());
                need.put(raw.getKey(), ing == null ? raw.getValue() : ing.requirement(raw.getValue()));
            }
            List<String> missing = new ArrayList<>();
            for (Map.Entry<String, Double> e : need.entrySet()) {
                Ingredient have = stock.get(e.getKey());
                if (have == null) { missing.add(e.getKey() + " (not in inventory)"); continue; }
                if (have.getQty() < e.getValue())                      // say exactly how much is needed, in stock and short, in the ingredient's own unit
                    missing.add(e.getKey() + " (needs " + have.format(e.getValue()) + " " + have.getUnit() + ", in stock " + have.fmtQty() + " "
                            + have.getUnit() + ", short by " + have.format(e.getValue() - have.getQty()) + " " + have.getUnit() + ")");
            }
            if (!missing.isEmpty()) return "Cannot confirm " + o.getId() + ". Not enough stock for: " + String.join("; ", missing) + ". Please restock first.";
            for (Map.Entry<String, Double> e : need.entrySet()) stock.get(e.getKey()).use(e.getValue());
            try {
                saveStock();
            } catch (IOException ex) {
                for (Map.Entry<String, Double> e : need.entrySet()) stock.get(e.getKey()).add(e.getValue());
                return "Could not save inventory; stock deduction was rolled back: " + ex.getMessage();
            }
            o.setStatus(OrderStatus.CONFIRMED);
            try {
                saveOrders();
            } catch (IOException ex) {
                o.setStatus(OrderStatus.PLACED);
                for (Map.Entry<String, Double> e : need.entrySet()) stock.get(e.getKey()).add(e.getValue());
                try {
                    saveStock();
                } catch (IOException rollbackEx) {
                    return "Could not save the order update: " + ex.getMessage()
                            + ". Inventory rollback also failed: " + rollbackEx.getMessage();
                }
                return "Could not save the order update; inventory deduction was rolled back: " + ex.getMessage();
            }
            fire(); return null;
        }
        void advance(Order o) throws IOException {
            OrderStatus previous = o.getStatus(), next = previous.next();
            if (next == null) return;
            o.setStatus(next);
            try {
                saveOrders();
            } catch (IOException ex) {
                o.setStatus(previous);
                throw new IOException("Could not save order update to " + ORDER_FILE + ": " + ex.getMessage(), ex);
            }
            fire();
        }
        void markPaid(Order o) throws IOException {
            PaymentStatus previous = o.getPaymentStatus();
            o.setPaymentStatus(PaymentStatus.PAID);
            try {
                saveOrders();
            } catch (IOException ex) {
                o.setPaymentStatus(previous);
                throw new IOException("Could not save order update to " + ORDER_FILE + ": " + ex.getMessage(), ex);
            }
            fire();
        }
        void restock(String name, double amt) throws IOException {
            Ingredient ingredient = stock.get(name);
            if (ingredient == null) throw new IllegalArgumentException("Unknown ingredient: " + name);
            double amount = ingredient.normalizeAmount(amt);        // finite, positive, sensible, whole for discrete items
            ingredient.add(amount);
            try {
                saveStock();
            } catch (IOException ex) {
                ingredient.add(-amount);
                throw ex;
            }
            fire();
        }
    }

    // =====================================================================
    //  IMAGE LOADING (your own local photos)
    // =====================================================================
    /**
     * Finds the photo for a product by NAME. It looks in these folders (relative to the folder the
     * program runs from):  Images, images, src/Images, src/images  (and the same names one level up).
     * A file matches when its name, ignoring capitals/spaces/underscores and the extension
     * (.png .jpg .jpeg), equals the product name, e.g. "Chocolate Truffle Cake.JPG" or
     * "chocolate_truffle_cake.png" both match the product "Chocolate Truffle Cake".
     * If nothing is found, load() returns null and the card simply shows a neutral empty area.
     */
    static class ImageLoader {
        private static final Map<String, BufferedImage> CACHE = new HashMap<>();    // loaded once, reused
        private static final String[] FOLDERS = {"Images", "images", "src/Images", "src/images", "../Images", "../images"};
        private static final String[] EXTENSIONS = {".png", ".jpg", ".jpeg"};
        private static List<File> folders = null;

        /** "Chocolate Truffle Cake" -> "chocolate-truffle-cake" */
        static String fileKey(String text) {
            return text.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        }

        /** Custom-cake option photo name: section + option, e.g. ("flavour", "Red Velvet") -> "flavour-red-velvet" (so Images/flavour-red-velvet.jpg). */
        static String optionKey(String section, String optionName) { return section + " " + optionName; }

        /** Finds the image folders once and prints where they are (shown in IntelliJ's Run window). */
        private static List<File> findFolders() {
            if (folders == null) {
                folders = new ArrayList<>();
                for (String name : FOLDERS) {
                    File dir = new File(name);
                    if (!dir.isDirectory()) continue;
                    try {
                        File real = dir.getCanonicalFile();
                        if (!folders.contains(real)) folders.add(real);
                    } catch (IOException ignored) { }
                }
                if (folders.isEmpty())
                    System.err.println("[Images] No Images folder found. Program is running from: " + new File(".").getAbsoluteFile().getParent()
                            + "  -> create a folder named Images there (next to src).");
                else
                    System.out.println("[Images] Loading product photos from: " + folders);
            }
            return folders;
        }

        private static File findFile(String key) {
            for (File dir : findFolders()) {
                File[] files = dir.listFiles();
                if (files == null) continue;
                for (File f : files) {
                    String lower = f.getName().toLowerCase();
                    for (String ext : EXTENSIONS)
                        if (lower.endsWith(ext) && fileKey(lower.substring(0, lower.length() - ext.length())).equals(key)) return f;
                }
            }
            return null;
        }

        static BufferedImage load(String productName) {
            String key = fileKey(productName);
            if (CACHE.containsKey(key)) return CACHE.get(key);
            BufferedImage img = null;
            try {
                File f = findFile(key);
                if (f != null) {
                    img = ImageIO.read(f);
                    if (img == null) System.err.println("[Images] Could not read (unsupported format?): " + f.getName());
                } else {
                    for (String folder : new String[]{"/Images/", "/images/"}) {       // also try inside the compiled output
                        for (String ext : EXTENSIONS) {
                            URL u = MicroBakeryApp.class.getResource(folder + key + ext);
                            if (u != null && img == null) img = ImageIO.read(u);
                        }
                    }
                    if (img == null) System.err.println("[Images] No photo found for \"" + productName + "\" (expected something like " + key + ".jpg)");
                }
            } catch (IOException | RuntimeException ex) {
                System.err.println("[Images] Problem loading \"" + productName + "\": " + ex.getMessage());
                img = null;
            }
//            if (img != null) img = shrink(img);
            CACHE.put(key, img);     // null is cached too, so a missing file is only searched once
            return img;
        }

        private static final Map<String, BufferedImage> FULL_CACHE = new HashMap<>();
        /** Original-resolution photo for the hero banner (only reduced if it is huge). Returns null quietly if the file does not exist. */
        static BufferedImage loadFull(String name) {
            String key = fileKey(name);
            if (FULL_CACHE.containsKey(key)) return FULL_CACHE.get(key);
            BufferedImage img = null;
            try {
                File f = findFile(key);
                if (f != null) img = ImageIO.read(f);
                else for (String folder : new String[]{"/Images/", "/images/"})
                    for (String ext : EXTENSIONS) {
                        URL u = MicroBakeryApp.class.getResource(folder + key + ext);
                        if (u != null && img == null) img = ImageIO.read(u);
                    }
            } catch (IOException | RuntimeException ex) {
                System.err.println("[Images] Problem loading \"" + name + "\": " + ex.getMessage());
                img = null;
            }
            if (img != null) {
                double s = Math.min(1.0, Math.min(2400.0 / img.getWidth(), 1400.0 / img.getHeight()));
                if (s < 1.0) {
                    BufferedImage out = new BufferedImage(Math.max(1, (int) (img.getWidth() * s)), Math.max(1, (int) (img.getHeight() * s)), BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = out.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.drawImage(img, 0, 0, out.getWidth(), out.getHeight(), null);
                    g.dispose(); img = out;
                }
            }
            FULL_CACHE.put(key, img);
            return img;
        }

        private static final Map<String, BufferedImage> FIT_CACHE = new HashMap<>();
        /**
         * Smooth, aspect-preserving copy of the ORIGINAL photo at exactly tw x th pixels, for painting only.
         * The file and the loaded original are never modified. Big photos are halved step by step before the
         * final scaling, which keeps edges clean (a single 1000 px -> 300 px jump looks jagged).
         */
        static BufferedImage fitted(String productName, BufferedImage src, int tw, int th) {
            if (src == null || tw < 1 || th < 1) return src;
            String key = fileKey(productName) + "@" + tw + "x" + th;
            BufferedImage hit = FIT_CACHE.get(key);
            if (hit != null) return hit;
            BufferedImage cur = src;
            if (tw < src.getWidth() || th < src.getHeight()) {
                int cw = src.getWidth(), ch = src.getHeight();
                while (cw / 2 >= tw && ch / 2 >= th) {
                    cw /= 2; ch /= 2;
                    cur = resample(cur, cw, ch, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                }
                cur = resample(cur, tw, th, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            }
            if (FIT_CACHE.size() > 600) FIT_CACHE.clear();
            FIT_CACHE.put(key, cur);
            return cur;
        }
        private static BufferedImage resample(BufferedImage in, int w, int h, Object interpolation) {
            BufferedImage out = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(in, 0, 0, out.getWidth(), out.getHeight(), null);
            g.dispose();
            return out;
        }

        /** Downsizes big photos once (keeps aspect ratio) so painting stays fast. */
        private static BufferedImage shrink(BufferedImage src) {
            int w = src.getWidth(), h = src.getHeight();
            double s = Math.min(1.0, Math.min(500.0 / w, 400.0 / h));
            if (s >= 1.0) return src;
            int nw = Math.max(1, (int) (w * s)), nh = Math.max(1, (int) (h * s));
            BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(src, 0, 0, nw, nh, null);
            g.dispose();
            return out;
        }
    }

    /**
     * Product picture in a rounded box. The box is locked to the photos' own proportions (1000 x 535) and fills the
     * card's inner width, so the COMPLETE picture is shown, never cropped, stretched or surrounded by empty space.
     * Keeps the optional Best Seller / Popular badge.
     */
    static class ProductImage extends JComponent {
        static final double RATIO = 1000.0 / 535.0;                  // width / height of the product photos
        private static final int FALLBACK_WIDTH = 262;               // used until the card has been laid out
        private final String productName; private final BufferedImage img; private final String badge;

        ProductImage(BakeryItem p) {
            productName = p.getName();
            img = ImageLoader.load(productName);
            badge = badgeFor(productName);
            setMinimumSize(new Dimension(120, 64));
            // once the card has its real width the box height follows it; ask the layout to recalculate if it differs
            addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent e) {
                    if (Math.abs(getPreferredSize().height - getHeight()) > 1) revalidate();
                }
            });
        }

        private int availableWidth() {
            Container c = getParent();
            if (c == null) return FALLBACK_WIDTH;
            Insets in = c.getInsets();
            int w = c.getWidth() - in.left - in.right;
            return w > 0 ? w : FALLBACK_WIDTH;
        }
        @Override public Dimension getPreferredSize() {
            int w = availableWidth();
            return new Dimension(w, (int) Math.round(w / RATIO));
        }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }

        /** #1 seller -> "Best Seller", #2 -> "Popular" (only if they have actually sold something). */
        private static String badgeFor(String name) {
            List<Map.Entry<String, Integer>> list = new ArrayList<>(Bakery.get().unitsSold().entrySet());
            list.sort((a, b) -> b.getValue() - a.getValue());
            if (list.size() > 0 && list.get(0).getKey().equals(name) && list.get(0).getValue() > 0) return "Best Seller";
            if (list.size() > 1 && list.get(1).getKey().equals(name) && list.get(1).getValue() > 0) return "Popular";
            return null;
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int w = getWidth(), h = getHeight();
            Shape box = new java.awt.geom.RoundRectangle2D.Double(0, 0, w, h, 16, 16);
            Shape inherited = g.getClip();                         // the scroll viewport's clip: must be kept, or the photo paints outside the menu area
            g.clip(box);                                           // keeps the picture inside the rounded corners (intersects with the inherited clip)
            g.setColor(C_LIGHT); g.fill(box);                      // neutral background (this is all you see if there is no photo)
            if (img != null) {
                // "contain" fit: the whole photo is visible and keeps its proportions. The box has the same proportions,
                // so the picture fills it completely; a photo of a different shape would simply get small margins.
                double s = Math.min((double) w / img.getWidth(), (double) h / img.getHeight());
                int dw = (int) Math.round(img.getWidth() * s), dh = (int) Math.round(img.getHeight() * s);
                if (Math.abs(dw - w) <= 2 && Math.abs(dh - h) <= 2) { dw = w; dh = h; }    // remove 1-2 px rounding gaps
                g.drawImage(ImageLoader.fitted(productName, img, dw, dh), (w - dw) / 2, (h - dh) / 2, dw, dh, null);
            }
            g.setClip(inherited);
            if (badge != null && img != null) {                    // pill badge in the top-left corner
                g.setFont(F_SMALL.deriveFont(Font.BOLD));
                FontMetrics fm = g.getFontMetrics();
                int bw = fm.stringWidth(badge) + 34, bh = 24;
                g.setColor(C_DARK); g.fillRoundRect(10, 10, bw, bh, bh, bh);
                g.setColor(C_SOFT); g.fillOval(18, 18, 8, 8);
                g.setColor(Color.WHITE);
                g.drawString(badge, 32, 10 + (bh + fm.getAscent() - fm.getDescent()) / 2);
            }
            g.dispose();
        }
    }

    // =====================================================================
    //  UI HELPERS & CUSTOM COMPONENTS
    // =====================================================================
    /** Plain body-size text that asks for the strong accent is drawn in the main text colour; bold captions and headings keep C_DARK. */
    static JLabel label(String t, Font f, Color c) {
        JLabel l = new JLabel(t); l.setFont(f);
        l.setForeground(c == C_DARK && !f.isBold() && f.getSize() <= 14 ? C_TEXT : c);
        return l;
    }
    static RoundButton primary(String t)   { return new RoundButton(t, C_ACCENT, Color.WHITE); }
    static RoundButton secondary(String t) { return new RoundButton(t, C_SOFT, C_DARK); }
    /** Fades a card to its hover look while the mouse is anywhere over it (including its child components). */
    static void hoverEffect(Component component, RoundedPanel card) {
        component.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { card.setHover(true); }
            @Override public void mouseExited(MouseEvent e) {
                if (!card.contains(SwingUtilities.convertPoint(component, e.getPoint(), card))) card.setHover(false);
            }
        });
        if (component instanceof Container) for (Component c : ((Container) component).getComponents()) hoverEffect(c, card);
    }
    static void info(Component p, String m) { JOptionPane.showMessageDialog(p, m, "Crumb & Whisk", JOptionPane.INFORMATION_MESSAGE); }
    static void warn(Component p, String m) { JOptionPane.showMessageDialog(p, m, "Please check", JOptionPane.WARNING_MESSAGE); }

    /** Draws a line-art bin / trash can centred on (cx, cy); r controls the size. Shared by the icon button and the dialog. */
    static void drawTrash(Graphics2D g0, double cx, double cy, double r, Color c, float stroke) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(c); g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(cx - r * 1.15, cy - r * 0.75, cx + r * 1.15, cy - r * 0.75));          // lid
        Path2D.Double handle = new Path2D.Double();
        handle.moveTo(cx - r * 0.45, cy - r * 0.75); handle.lineTo(cx - r * 0.45, cy - r * 1.3);
        handle.lineTo(cx + r * 0.45, cy - r * 1.3); handle.lineTo(cx + r * 0.45, cy - r * 0.75);
        g.draw(handle);
        Path2D.Double body = new Path2D.Double();
        body.moveTo(cx - r * 0.85, cy - r * 0.4); body.lineTo(cx - r * 0.7, cy + r * 1.3);
        body.lineTo(cx + r * 0.7, cy + r * 1.3); body.lineTo(cx + r * 0.85, cy - r * 0.4);
        g.draw(body);
        g.draw(new Line2D.Double(cx - r * 0.3, cy - r * 0.05, cx - r * 0.3, cy + r * 0.85));            // slats
        g.draw(new Line2D.Double(cx + r * 0.3, cy - r * 0.05, cx + r * 0.3, cy + r * 0.85));
        g.dispose();
    }

    /**
     * Themed "remove this item?" dialog. Returns true only when the customer presses Remove;
     * Cancel, the close button and Esc all return false and leave the cart untouched.
     */
    static boolean confirmRemove(Component parent, String productName, int qty) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "Remove item", Dialog.ModalityType.APPLICATION_MODAL);
        final boolean[] confirmed = {false};

        JComponent badge = new JComponent() {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(blend(C_ALERT, Color.WHITE, 0.88f)); g.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                drawTrash(g, getWidth() / 2.0, getHeight() / 2.0 + 1, 11, C_ALERT, 2.4f);
                g.dispose();
            }
        };
        badge.setPreferredSize(new Dimension(64, 64));
        JPanel iconWrap = new JPanel(new BorderLayout()); iconWrap.setOpaque(false); iconWrap.add(badge, BorderLayout.NORTH);

        String safeName = productName.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        JLabel title = label("Remove this item?", F_H2, C_DARK);
        JLabel message = label("<html><div style='width:250px'>Do you want to remove <b>" + safeName + "</b> ("
                + qty + " in your cart) from your cart?</div></html>", F_BODY, C_TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT); message.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel text = new JPanel(); text.setOpaque(false); text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(title); text.add(Box.createVerticalStrut(8)); text.add(message);

        JPanel row = new JPanel(new BorderLayout(18, 0)); row.setOpaque(false);
        row.add(iconWrap, BorderLayout.WEST); row.add(text, BorderLayout.CENTER);

        RoundButton cancel = secondary("Cancel");
        RoundButton remove = new RoundButton("Remove", C_ALERT, Color.WHITE);
        cancel.setPreferredSize(new Dimension(110, 40)); remove.setPreferredSize(new Dimension(110, 40));
        cancel.addActionListener(e -> dialog.dispose());
        remove.addActionListener(e -> { confirmed[0] = true; dialog.dispose(); });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0)); buttons.setOpaque(false);
        buttons.add(cancel); buttons.add(remove);

        JPanel root = new JPanel(new BorderLayout(0, 20)); root.setBackground(Color.WHITE);
        root.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(C_SOFT), new EmptyBorder(22, 24, 18, 24)));
        root.add(row, BorderLayout.CENTER); root.add(buttons, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.getRootPane().setDefaultButton(cancel);                    // Enter = the safe choice
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setResizable(false);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);                                          // modal: returns once the dialog is closed
        return confirmed[0];
    }

    static void styleField(JComponent c) {
        c.setFont(F_BODY);
        c.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(C_SOFT), new EmptyBorder(6, 8, 6, 8)));
        // active state: the outline turns navy-slate while the field has the keyboard focus (same thickness, so nothing shifts)
        c.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                c.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(C_ACCENT), new EmptyBorder(6, 8, 6, 8))); }
            @Override public void focusLost(FocusEvent e) {
                c.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(C_SOFT), new EmptyBorder(6, 8, 6, 8))); }
        });
    }
    /** Bakery-style dietary dropdown: rounded field, warm border that deepens on hover/focus, chevron, padded options with a clear selection state. */
    static void styleDietCombo(JComboBox<?> c) {
        c.setFont(F_BODY.deriveFont(Font.BOLD)); c.setForeground(C_DARK); c.setOpaque(false);
        c.setUI(new DietComboUI());
        c.setBorder(new EmptyBorder(0, 0, 0, 0));
        c.setMaximumRowCount(8);
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        c.setPreferredSize(new Dimension(190, 34));
        c.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, sel, false);
                boolean current = index >= 0 && list.getModel().getSize() > 0 && value != null && value.equals(c.getSelectedItem());
                l.setFont(F_BODY.deriveFont(current || index < 0 ? Font.BOLD : Font.PLAIN));
                l.setBorder(new EmptyBorder(index < 0 ? 0 : 8, index < 0 ? 4 : 14, index < 0 ? 0 : 8, 14));
                l.setOpaque(index >= 0);
                l.setForeground(index < 0 ? C_DARK : sel ? Color.WHITE : current ? C_ACCENT : C_TEXT);
                l.setBackground(sel ? C_ACCENT : current ? C_TINT : Color.WHITE);
                return l;
            }
        });
    }
    static final class DietComboUI extends javax.swing.plaf.basic.BasicComboBoxUI {
        private boolean hover;
        @Override protected void installListeners() {
            super.installListeners();
            comboBox.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseEntered(java.awt.event.MouseEvent e) { hover = true; comboBox.repaint(); }
                @Override public void mouseExited(java.awt.event.MouseEvent e) { hover = false; comboBox.repaint(); }
            });
        }
        @Override protected JButton createArrowButton() {
            JButton b = new JButton() {
                @Override protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int cx = getWidth() / 2, cy = getHeight() / 2;
                    g.setColor(C_ACCENT); g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    boolean up = comboBox.isPopupVisible();
                    int d = up ? -3 : 3;
                    g.drawLine(cx - 5, cy - d / 2, cx, cy + d / 2); g.drawLine(cx, cy + d / 2, cx + 5, cy - d / 2);
                    g.dispose();
                }
            };
            b.setBorder(new EmptyBorder(0, 0, 0, 0)); b.setContentAreaFilled(false); b.setFocusPainted(false); b.setOpaque(false);
            b.setPreferredSize(new Dimension(30, 30));
            return b;
        }
        @Override public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) { }
        @Override public void paint(Graphics g0, JComponent c) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean active = comboBox.isPopupVisible() || comboBox.hasFocus();
            g.setColor(active || hover ? Color.WHITE : new Color(0xFFFDFD));
            g.fillRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, 18, 18);
            g.setColor(active ? C_ACCENT : hover ? C_ACCENT : C_SOFT);
            g.setStroke(new BasicStroke(active ? 2f : 1.4f));
            g.drawRoundRect(active ? 1 : 0, active ? 1 : 0, c.getWidth() - (active ? 3 : 1), c.getHeight() - (active ? 3 : 1), 18, 18);
            g.dispose();
            super.paint(g0, c);
        }
        @Override protected javax.swing.plaf.basic.ComboPopup createPopup() {
            javax.swing.plaf.basic.BasicComboPopup p = new javax.swing.plaf.basic.BasicComboPopup(comboBox);
            p.setBorder(BorderFactory.createCompoundBorder(new javax.swing.border.LineBorder(C_SOFT, 1, true), new EmptyBorder(4, 4, 4, 4)));
            p.setBackground(Color.WHITE);
            p.getList().setBackground(Color.WHITE);
            return p;
        }
    }
    static void styleCombo(JComboBox<?> c) { c.setFont(F_BODY); c.setBackground(Color.WHITE); c.setForeground(C_TEXT); }

    static DefaultTableModel readOnlyModel(String... cols) {
        return new DefaultTableModel(cols, 0) { @Override public boolean isCellEditable(int r, int c) { return false; } };
    }
    static void styleTable(JTable t) {
        t.setRowHeight(28); t.setFont(F_BODY); t.setGridColor(blend(C_SOFT, Color.WHITE, 0.45f)); t.setBackground(Color.WHITE);
        t.setForeground(C_TEXT);
        t.setSelectionBackground(C_ACCENT); t.setSelectionForeground(Color.WHITE); t.setFillsViewportHeight(true);
        // header cells: deep wine -> primary gradient with light text (cells are drawn one by one, so the gradient runs top to bottom)
        DefaultTableCellRenderer hr = new DefaultTableCellRenderer() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, C_DARK, 0, getHeight(), blend(C_DARK, C_ACCENT, 0.7f)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(255, 255, 255, 38)); g2.drawLine(getWidth() - 1, 4, getWidth() - 1, getHeight() - 5);   // faint column divider
                g2.dispose(); super.paintComponent(g);
            }
        };
        hr.setOpaque(false); hr.setBackground(C_DARK); hr.setForeground(Color.WHITE); hr.setFont(F_BOLD);
        t.getTableHeader().setDefaultRenderer(hr);
        t.getTableHeader().setReorderingAllowed(false);
    }
    static JScrollPane scroll(Component c) {
        JScrollPane s = new JScrollPane(c);
        s.setBorder(BorderFactory.createLineBorder(C_SOFT));
        s.getViewport().setBackground(Color.WHITE);
        s.getVerticalScrollBar().setUnitIncrement(16);
        return s;
    }
    static void addRow(JPanel p, int row, String text, JComponent c) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridy = row; g.insets = new Insets(6, 4, 6, 10); g.anchor = GridBagConstraints.NORTHWEST;
        g.gridx = 0; JLabel cap = label(text, F_BOLD, C_DARK); cap.setBorder(new EmptyBorder(7, 0, 0, 0)); p.add(cap, g);
        g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL; p.add(c, g);
    }
    static void onTextChange(JTextComponent c, Runnable r) {
        c.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { r.run(); }
            public void removeUpdate(DocumentEvent e) { r.run(); }
            public void changedUpdate(DocumentEvent e) { r.run(); }
        });
    }

    /** Limits the number of characters in a text field (used for the cake message). */
    static class LimitFilter extends DocumentFilter {
        private final int max;
        LimitFilter(int max) { this.max = max; }
        @Override public void insertString(FilterBypass fb, int off, String s, AttributeSet a) throws BadLocationException { replace(fb, off, 0, s, a); }
        @Override public void replace(FilterBypass fb, int off, int len, String s, AttributeSet a) throws BadLocationException {
            if (s == null) s = "";
            if (fb.getDocument().getLength() - len + s.length() <= max) super.replace(fb, off, len, s, a);
            else Toolkit.getDefaultToolkit().beep();
        }
    }

    /** Keeps the phone field to local digits only (the +92 code is shown beside the field, never typed). */
    static class LocalPhoneFilter extends DocumentFilter {
        @Override public void insertString(FilterBypass fb, int off, String s, AttributeSet a) throws BadLocationException { replace(fb, off, 0, s, a); }
        @Override public void replace(FilterBypass fb, int off, int len, String s, AttributeSet a) throws BadLocationException {
            javax.swing.text.Document doc = fb.getDocument();
            String next = new StringBuilder(doc.getText(0, doc.getLength())).replace(off, off + len, s == null ? "" : s).toString();
            super.replace(fb, 0, doc.getLength(), normalizeLocalPhone(next), a);
        }
    }

    static class RoundedPanel extends JPanel {
        private Color fill; private final int arc;
        private float hover = 0f; private boolean hovering = false;
        private final javax.swing.Timer hoverTimer = new javax.swing.Timer(16, e -> {
            float target = hovering ? 1f : 0f;
            hover += (target - hover) * 0.35f;
            if (Math.abs(target - hover) < 0.02f) { hover = target; ((javax.swing.Timer) e.getSource()).stop(); }
            repaint();
        });
        RoundedPanel(LayoutManager lm, Color fill, int arc) { super(lm); this.fill = fill; this.arc = arc; setOpaque(false); }
        void setFill(Color c) { fill = c; repaint(); }
        /** Smoothly fades the card to its hover look (warm fill + accent border) and back. */
        void setHover(boolean on) { hovering = on; hoverTimer.start(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // White cards get a soft drop shadow (drawn inside the panel's own bounds, so no layout changes); it deepens on hover.
            int sh = Color.WHITE.equals(fill) ? 4 : 0, bw = getWidth() - 1 - (sh > 0 ? 2 : 0), bh = getHeight() - 1 - sh;
            if (sh > 0) {
                int a = Math.round(44 + 30 * hover);
                for (int k = 3; k >= 1; k--) {
                    g2.setColor(new Color(78, 32, 32, Math.max(2, a / (k + 1))));
                    g2.fillRoundRect(k / 2, k, bw, bh, arc, arc);
                }
            }
            g2.setColor(hover > 0 ? blend(fill, C_HOVER, hover) : fill);
            g2.fillRoundRect(0, 0, bw, bh, arc, arc);
            g2.setColor(hover > 0 ? blend(C_SOFT, C_ACCENT, hover) : blend(C_SOFT, Color.WHITE, sh > 0 ? 0.35f : 0f));
            g2.drawRoundRect(0, 0, bw, bh, arc, arc);
            g2.dispose(); super.paintComponent(g);
        }
    }

    /** Panel with a left-to-right colour gradient (used for the header bar). */
    static class GradientPanel extends JPanel {
        private final Color from, to;
        GradientPanel(LayoutManager lm, Color from, Color to) { super(lm); this.from = from; this.to = to; setOpaque(false); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setPaint(new GradientPaint(0, 0, from, getWidth(), 0, to));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose(); super.paintComponent(g);
        }
    }

    /** Small coloured status dot. */
    static class StatusDot extends JComponent {
        private Color color;
        StatusDot(Color c) { color = c; Dimension d = new Dimension(10, 10); setPreferredSize(d); setMinimumSize(d); setMaximumSize(d); }
        void setColor(Color c) { color = c; repaint(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color); g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1); g2.dispose();
        }
    }

    /** Rounded button. Can show an Icon (before the text) and an optional count badge (used by the Cart button). */
    static class RoundButton extends JButton {
        private Color base; private boolean over = false; private Integer badge = null;
        private float hover = 0f;                                   // 0 = resting, 1 = fully hovered (animated)
        private final javax.swing.Timer hoverTimer = new javax.swing.Timer(16, e -> {
            float target = over ? 1f : 0f;
            hover += (target - hover) * 0.35f;
            if (Math.abs(target - hover) < 0.02f) { hover = target; ((javax.swing.Timer) e.getSource()).stop(); }
            repaint();
        });
        RoundButton(String text, Color base, Color fg) {
            super(text); this.base = base;
            setForeground(fg); setFont(F_BOLD);
            setContentAreaFilled(false); setFocusPainted(false); setBorderPainted(false); setOpaque(false);
            setBorder(new EmptyBorder(9, 18, 9, 18));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { over = true; hoverTimer.start(); }
                public void mouseExited(MouseEvent e) { over = false; hoverTimer.start(); }
            });
        }
        void recolor(Color bg, Color fg) { base = bg; setForeground(fg); repaint(); }
        /** Hover colour that keeps the text readable: dark buttons lift towards the soft rose, light buttons get lighter. */
        private static Color hoverColor(Color b, float t) {
            if (t <= 0f) return b;
            double lum = (0.299 * b.getRed() + 0.587 * b.getGreen() + 0.114 * b.getBlue()) / 255.0;
            if (lum >= 0.9) return blend(b, C_LIGHT, t);
            if (lum >= 0.6) return blend(b, Color.WHITE, 0.45f * t);
            if (b.equals(C_ALERT)) return blend(b, Color.BLACK, 0.25f * t);   // the red warning button keeps its own look
            return blend(b, C_SOFT, 0.30f * t);                        // white text stays above 4.5:1 even fully hovered
        }
        /** Primary / active buttons get the gradient sheen; light and warning buttons stay flat. */
        private static boolean isDark(Color b) {
            return !b.equals(C_ALERT) && (0.299 * b.getRed() + 0.587 * b.getGreen() + 0.114 * b.getBlue()) / 255.0 < 0.6;
        }
        /** Shows a small number badge on the right side of the button (always shows the value, including 0). */
        void setBadge(int n) {
            if (badge == null) setBorder(new EmptyBorder(9, 18, 9, 52));   // reserve room for the badge once
            badge = n; repaint();
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color c = !isEnabled() ? C_SOFT : hoverColor(base, hover);
            if (isEnabled() && getModel().isPressed()) c = blend(c, Color.BLACK, 0.18f);             // pressed: a touch deeper
            if (isEnabled() && isDark(base)) {
                // dark (primary / active) buttons: deep wine -> soft rose sheen from left to right, with a light top edge
                g2.setPaint(new GradientPaint(0, 0, blend(c, C_DARK, 0.40f), getWidth(), 0, blend(c, C_SOFT, 0.22f)));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g2.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 34), 0, getHeight() / 2f, new Color(255, 255, 255, 0)));
                g2.fillRoundRect(0, 0, getWidth(), getHeight() / 2, 18, 18);
                g2.setColor(new Color(255, 255, 255, 45)); g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);   // hairline keeps the button visible on the header gradient
            } else {
                g2.setPaint(new GradientPaint(0, 0, blend(c, Color.WHITE, 0.12f), 0, getHeight(), c));   // subtle top-light gradient
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
            }
            g2.dispose();
            super.paintComponent(g);                                       // draws icon + text
            if (badge != null) {
                Graphics2D b = (Graphics2D) g.create();
                b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                b.setFont(F_SMALL.deriveFont(Font.BOLD));
                String t = badge > 99 ? "99+" : String.valueOf(badge);
                FontMetrics fm = b.getFontMetrics();
                int w = Math.max(22, fm.stringWidth(t) + 12), h = 22, bx = getWidth() - w - 14, by = (getHeight() - h) / 2;
                b.setColor(getForeground()); b.fillRoundRect(bx, by, w, h, h, h);      // pill uses the text colour...
                b.setColor(base);                                                      // ...and the number uses the button colour
                b.drawString(t, bx + (w - fm.stringWidth(t)) / 2, by + (h + fm.getAscent() - fm.getDescent()) / 2);
                b.dispose();
            }
        }
    }

    /**
     * Round icon-only button drawn with Java2D (no image files): a "+", "-" or bin glyph.
     * "filled" buttons use the primary wine gradient; the others are white discs with a soft border.
     */
    static class CircleIconButton extends JButton {
        enum Kind { PLUS, MINUS, TRASH }
        private final Kind kind; private final boolean filled;
        private boolean over = false; private float hover = 0f;
        private final javax.swing.Timer hoverTimer = new javax.swing.Timer(16, e -> {
            float target = over ? 1f : 0f;
            hover += (target - hover) * 0.35f;
            if (Math.abs(target - hover) < 0.02f) { hover = target; ((javax.swing.Timer) e.getSource()).stop(); }
            repaint();
        });
        CircleIconButton(Kind kind, boolean filled, int size, String tip) {
            this.kind = kind; this.filled = filled;
            Dimension d = new Dimension(size, size);
            setPreferredSize(d); setMinimumSize(d); setMaximumSize(d);
            setContentAreaFilled(false); setFocusPainted(false); setBorderPainted(false); setOpaque(false);
            setBorder(null);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(tip); getAccessibleContext().setAccessibleName(tip);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { over = true; hoverTimer.start(); }
                @Override public void mouseExited(MouseEvent e) { over = false; hoverTimer.start(); }
            });
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            int w = getWidth(), h = getHeight(), d = Math.min(w, h) - 1;
            boolean pressed = getModel().isPressed();
            Color glyph;
            if (filled) {
                Color c = blend(C_ACCENT, C_SOFT, 0.30f * hover);
                if (pressed) c = blend(c, Color.BLACK, 0.18f);
                g.setPaint(new GradientPaint(0, 0, blend(c, C_DARK, 0.40f), w, 0, blend(c, C_SOFT, 0.22f)));
                g.fillOval(0, 0, d, d);
                g.setColor(new Color(255, 255, 255, 45)); g.drawOval(0, 0, d - 1, d - 1);
                glyph = Color.WHITE;
            } else {
                boolean bin = kind == Kind.TRASH;
                Color bg = bin ? blend(Color.WHITE, blend(C_ALERT, Color.WHITE, 0.88f), hover) : blend(Color.WHITE, C_TINT, hover);
                if (pressed) bg = blend(bg, Color.BLACK, 0.08f);
                g.setColor(bg); g.fillOval(0, 0, d, d);
                g.setColor(bin ? blend(C_SOFT, C_ALERT, hover) : blend(C_SOFT, C_ACCENT, hover));
                g.setStroke(new BasicStroke(1.2f)); g.drawOval(0, 0, d - 1, d - 1);
                glyph = bin ? C_ALERT : C_DARK;
            }
            double cx = w / 2.0, cy = h / 2.0, r = d * 0.2;
            g.setColor(glyph); g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (kind) {
                case PLUS:  g.draw(new Line2D.Double(cx - r, cy, cx + r, cy)); g.draw(new Line2D.Double(cx, cy - r, cx, cy + r)); break;
                case MINUS: g.draw(new Line2D.Double(cx - r, cy, cx + r, cy)); break;
                default:    drawTrash(g, cx, cy, d * 0.17, glyph, 1.9f);
            }
            if (isFocusOwner()) {
                g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{2f, 2f}, 0f));
                g.setColor(C_DARK); g.drawOval(2, 2, d - 5, d - 5);
            }
            g.dispose();
        }
    }

    /** Simple line icons drawn with Java2D. They take the button's text colour, so active/inactive styles just work. */
    static class NavIcon implements Icon {
        enum Type { DASHBOARD, CAKE_SLICE, CART, PACKAGE_SEARCH }
        private static final int SIZE = 20;
        private final Type type;
        NavIcon(Type type) { this.type = type; }
        @Override public int getIconWidth() { return SIZE; }
        @Override public int getIconHeight() { return SIZE; }
        @Override public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.translate(x, y);
            g.setColor(c.getForeground());
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (type) {
                case DASHBOARD:                                     // layout-dashboard
                    g.drawRoundRect(2, 2, 6, 10, 2, 2); g.drawRoundRect(12, 2, 6, 5, 2, 2);
                    g.drawRoundRect(12, 11, 6, 7, 2, 2); g.drawRoundRect(2, 15, 6, 3, 2, 2);
                    break;
                case CAKE_SLICE: {                                  // wedge of cake with a cherry
                    Path2D wedge = new Path2D.Double();
                    wedge.moveTo(2, 16); wedge.lineTo(2, 9); wedge.lineTo(17, 6); wedge.lineTo(17, 16); wedge.closePath();
                    g.draw(wedge);
                    g.draw(new Line2D.Double(2, 12.5, 17, 11.2));
                    g.draw(new Ellipse2D.Double(8, 1.5, 3.5, 3.5));
                    break;
                }
                case CART: {                                        // shopping cart
                    Path2D cart = new Path2D.Double();
                    cart.moveTo(1, 2.5); cart.lineTo(4, 2.5); cart.lineTo(6.6, 12.5); cart.lineTo(16, 12.5);
                    cart.lineTo(18.2, 5.5); cart.lineTo(4.7, 5.5);
                    g.draw(cart);
                    g.fill(new Ellipse2D.Double(6.5, 15, 3.2, 3.2)); g.fill(new Ellipse2D.Double(13.5, 15, 3.2, 3.2));
                    break;
                }
                default:                                            // package + magnifier
                    g.drawRoundRect(2, 3, 11, 10, 2, 2); g.drawLine(2, 7, 13, 7); g.drawLine(7, 3, 7, 7);
                    g.draw(new Ellipse2D.Double(10.5, 10.5, 6, 6)); g.drawLine(16, 16, 19, 19);
                    break;
            }
            g.dispose();
        }
    }

    /** Simple drawn cupcake logo. */
    static class Logo extends JComponent {
        Logo(int size) { setPreferredSize(new Dimension(size, size)); setMaximumSize(new Dimension(size, size)); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(C_ACCENT); g2.fillOval(0, 0, w, h);
            g2.setColor(C_DARK);
            g2.fillPolygon(new int[]{(int) (.30 * w), (int) (.70 * w), (int) (.64 * w), (int) (.36 * w)},
                    new int[]{(int) (.55 * h), (int) (.55 * h), (int) (.80 * h), (int) (.80 * h)}, 4);
            g2.setColor(C_LIGHT); g2.fillOval((int) (.24 * w), (int) (.30 * h), (int) (.52 * w), (int) (.34 * h));
            g2.setColor(C_SOFT);  g2.fillOval((int) (.34 * w), (int) (.18 * h), (int) (.32 * w), (int) (.28 * h));
            g2.setColor(C_DARK);  g2.fillOval((int) (.46 * w), (int) (.11 * h), (int) (.10 * w), (int) (.10 * h));
            g2.dispose();
        }
    }

    /** A panel that always matches the scroll pane's width, so the menu never needs horizontal scrolling. */
    static class WidthTrackingPanel extends JPanel implements Scrollable {
        WidthTrackingPanel(LayoutManager lm) { super(lm); }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(100, r.height - 40); }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /** Horizontal bar chart used by the popularity tracker. */
    static class BarChart extends JPanel {
        private List<Map.Entry<String, Integer>> data = new ArrayList<>();
        BarChart() { setBackground(Color.WHITE); }
        void setData(List<Map.Entry<String, Integer>> d) {
            data = d; setPreferredSize(new Dimension(600, d.size() * 30 + 20)); revalidate(); repaint();
        }
        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int max = 1; for (Map.Entry<String, Integer> e : data) max = Math.max(max, e.getValue());
            int labelW = 260, avail = Math.max(50, getWidth() - labelW - 80);
            for (int i = 0; i < data.size(); i++) {
                int y = 10 + i * 30; Map.Entry<String, Integer> e = data.get(i);
                g.setFont(F_BODY); g.setColor(C_TEXT); g.drawString((i + 1) + ". " + e.getKey(), 10, y + 17);
                int w = (int) (avail * (double) e.getValue() / max);
                g.setColor(i < 3 ? C_ACCENT : (i >= data.size() - 3 ? new Color(0x7A7A7A) : C_SOFT));
                g.fillRoundRect(labelW, y, Math.max(w, 2), 22, 8, 8);
                g.setColor(C_TEXT); g.drawString(String.valueOf(e.getValue()), labelW + w + 8, y + 17);
            }
            g.dispose();
        }
    }

    /** Draws the 5-step order tracking timeline. */
    static class TimelinePanel extends JPanel {
        private Order order;
        TimelinePanel() { setOpaque(false); setPreferredSize(new Dimension(700, 120)); }
        void setOrder(Order o) { order = o; repaint(); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            OrderStatus[] st = OrderStatus.values(); int n = st.length, margin = 90, y = 40;
            int cur = order == null ? -1 : order.getStatus().ordinal();
            int step = (getWidth() - 2 * margin) / (n - 1);
            g.setStroke(new BasicStroke(4));
            for (int i = 0; i < n - 1; i++) { g.setColor(i < cur ? C_ACCENT : C_SOFT); g.drawLine(margin + i * step, y, margin + (i + 1) * step, y); }
            for (int i = 0; i < n; i++) {
                int x = margin + i * step; boolean done = i <= cur;
                g.setColor(done ? C_ACCENT : C_SOFT); g.fillOval(x - 16, y - 16, 32, 32);
                if (i == cur) { g.setColor(C_DARK); g.setStroke(new BasicStroke(3)); g.drawOval(x - 21, y - 21, 42, 42); }
                g.setFont(F_BOLD); FontMetrics fm = g.getFontMetrics();
                String number = String.valueOf(i + 1);
                g.setColor(done ? Color.WHITE : C_DARK); g.drawString(number, x - fm.stringWidth(number) / 2, y + 5);
                g.setFont(F_SMALL); fm = g.getFontMetrics(); g.setColor(C_DARK);
                String t = st[i].label(order != null && order.isDelivery());
                g.drawString(t, x - fm.stringWidth(t) / 2, y + 45);
            }
            g.dispose();
        }
    }

    // =====================================================================
    //  OPERATING HOURS (configurable) + HERO BANNER
    // =====================================================================
    /**
     * Opening hours behind the status badge. Defaults: Mon-Sat, 9 AM - 8 PM. To change them, create the text file
     * data/hours.properties (next to orders.ser) with any of:
     *   open=09:00     close=20:00     days=MON,TUE,WED,THU,FRI,SAT     accepting=true
     * Set accepting=false to show "Pre-orders paused" (e.g. holidays). Times use this computer's clock.
     * The badge only DISPLAYS these hours; it does not block checkout.
     */
    static class OperatingHours {
        static final File CONFIG_FILE = new File(new File(System.getProperty("user.dir"), "data"), "hours.properties");
        private LocalTime open = LocalTime.of(9, 0), close = LocalTime.of(20, 0);
        private Set<DayOfWeek> days = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.SATURDAY);
        private boolean accepting = true;

        static OperatingHours load() {
            OperatingHours h = new OperatingHours();
            if (!CONFIG_FILE.isFile()) return h;
            Properties p = new Properties();
            try (FileInputStream in = new FileInputStream(CONFIG_FILE)) { p.load(in); }
            catch (IOException ex) { System.err.println("[Hours] Could not read " + CONFIG_FILE + ": " + ex.getMessage() + " (using defaults)"); return h; }
            try {
                LocalTime o = LocalTime.parse(p.getProperty("open", "09:00").trim()), c = LocalTime.parse(p.getProperty("close", "20:00").trim());
                if (!c.isAfter(o)) throw new IllegalArgumentException("close must be later than open");
                Set<DayOfWeek> d = EnumSet.noneOf(DayOfWeek.class);
                for (String t : p.getProperty("days", "MON,TUE,WED,THU,FRI,SAT").split(",")) if (!t.isBlank()) d.add(parseDay(t.trim()));
                h.open = o; h.close = c; h.days = d;
                h.accepting = !"false".equalsIgnoreCase(p.getProperty("accepting", "true").trim());
            } catch (RuntimeException ex) {
                System.err.println("[Hours] Ignoring invalid " + CONFIG_FILE + ": " + ex.getMessage() + " (using defaults)");
            }
            return h;
        }
        private static DayOfWeek parseDay(String s) {
            String k = s.toUpperCase(Locale.ROOT);
            for (DayOfWeek d : DayOfWeek.values()) if (k.length() >= 3 && d.name().startsWith(k.substring(0, 3))) return d;
            throw new IllegalArgumentException("unknown day '" + s + "'");
        }
        private static String fmt(LocalTime t) {
            return t.format(DateTimeFormatter.ofPattern(t.getMinute() == 0 ? "h a" : "h:mm a", Locale.ENGLISH));
        }
        private static String dayName(DayOfWeek d) { return d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH); }

        boolean isAccepting() { return accepting; }
        boolean isOpen(LocalDateTime now) {
            LocalTime t = now.toLocalTime();
            return days.contains(now.getDayOfWeek()) && !t.isBefore(open) && t.isBefore(close);
        }
        /** The next opening time after "now", or null if no opening day is configured. */
        LocalDateTime nextOpening(LocalDateTime now) {
            for (int i = 0; i <= 7; i++) {
                LocalDateTime t = now.toLocalDate().plusDays(i).atTime(open);
                if (days.contains(t.getDayOfWeek()) && t.isAfter(now)) return t;
            }
            return null;
        }
        /** Text for the status badge, for the given moment. */
        String statusText(LocalDateTime now) {
            if (!accepting) return "Pre-orders paused";
            if (isOpen(now)) return "Open now · pre-orders until " + fmt(close);
            LocalDateTime next = nextOpening(now);
            if (next == null) return "Closed";
            long dayDiff = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), next.toLocalDate());
            String when = dayDiff == 0 ? "today" : dayDiff == 1 ? "tomorrow" : dayName(next.getDayOfWeek());
            return "Closed now · pre-orders reopen " + when + " " + fmt(open);
        }
        /** Short description such as "Mon-Sat 9 AM-8 PM". */
        String summary() {
            if (days.isEmpty()) return "Closed";
            List<DayOfWeek> list = new ArrayList<>(days); Collections.sort(list);
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < list.size(); ) {
                int j = i;
                while (j + 1 < list.size() && list.get(j + 1).ordinal() == list.get(j).ordinal() + 1) j++;
                parts.add(i == j ? dayName(list.get(i)) : j == i + 1 ? dayName(list.get(i)) + ", " + dayName(list.get(j))
                        : dayName(list.get(i)) + "–" + dayName(list.get(j)));
                i = j + 1;
            }
            return String.join(", ", parts) + " " + fmt(open) + "–" + fmt(close);
        }
    }

    /** Full-width banner: pastry photo (if one exists) under a dark gradient, fading-in headline, status badge and a button. */
    static class HeroBanner extends JPanel {
        static final String HEADLINE = "Crumb & Whisk";
        private static final String TAGLINE = "Baked Fresh Daily";
        private static final String[] PHOTO_NAMES = {"Hero Banner", "Hero", "Bakery Hero", "Assorted Pastries Box", "Butter Croissants", "Almond Danish"};
        private static final int MARGIN = 56;
        private final BufferedImage photo;
        private BufferedImage scaled; private int scaledW, scaledH;
        private final RoundButton cta = new RoundButton("Explore menu", Color.WHITE, C_DARK);
        private String statusText = " "; private Color statusColor = C_SOFT;
        private float fade = 0f;
        private final javax.swing.Timer fadeTimer = new javax.swing.Timer(30, e -> {
            fade = Math.min(1f, fade + 0.05f);
            if (fade >= 1f) ((javax.swing.Timer) e.getSource()).stop();
            repaint();
        });

        HeroBanner(Runnable onExplore) {
            setLayout(new BorderLayout()); setOpaque(true); setBackground(C_DARK);
            Dimension d = new Dimension(0, 360);
            setPreferredSize(d); setMinimumSize(new Dimension(0, 330));
            BufferedImage img = null;
            for (String n : PHOTO_NAMES) { img = ImageLoader.loadFull(n); if (img != null) break; }
            photo = img;
            cta.setFont(F_BOLD.deriveFont(15f)); cta.setBorder(new EmptyBorder(11, 26, 11, 26));
            cta.addActionListener(e -> onExplore.run());
            JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); south.setOpaque(false);
            south.setBorder(new EmptyBorder(0, MARGIN, 44, 0)); south.add(cta);
            add(south, BorderLayout.SOUTH);
            // the headline fades in every time the home page becomes visible
            addHierarchyListener(e -> {
                if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) { fade = 0f; fadeTimer.restart(); }
            });
        }
        boolean hasPhoto() { return photo != null; }
        void setStatus(String text, Color dot) { statusText = text; statusColor = dot; repaint(); }

        private List<String> wrap(String text, FontMetrics fm, int maxW) {
            List<String> lines = new ArrayList<>(); StringBuilder cur = new StringBuilder();
            for (String w : text.split(" ")) {
                String t = cur.length() == 0 ? w : cur + " " + w;
                if (fm.stringWidth(t) > maxW && cur.length() > 0) { lines.add(cur.toString()); cur = new StringBuilder(w); } else cur = new StringBuilder(t);
            }
            if (cur.length() > 0) lines.add(cur.toString());
            return lines;
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            if (photo != null) {                                   // photo, scaled to cover the banner (cached per size)
                if (scaled == null || scaledW != w || scaledH != h) {
                    double s = Math.max((double) w / photo.getWidth(), (double) h / photo.getHeight());
                    int dw = (int) Math.ceil(photo.getWidth() * s), dh = (int) Math.ceil(photo.getHeight() * s);
                    scaled = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_RGB);
                    Graphics2D sg = scaled.createGraphics();
                    sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    sg.drawImage(photo, (w - dw) / 2, (h - dh) / 2, dw, dh, null); sg.dispose();
                    scaledW = w; scaledH = h;
                }
                g.drawImage(scaled, 0, 0, null);
            } else {
                g.setPaint(new GradientPaint(0, 0, C_DARK, w, h, C_ACCENT));
                g.fillRect(0, 0, w, h);
            }
            int a = photo != null ? 215 : 60;                       // navy gradient overlay keeps the text readable on any photo
            g.setPaint(new GradientPaint(0, 0, new Color(34, 10, 10, a), w * 0.85f, 0, new Color(34, 10, 10, photo != null ? 60 : 0)));
            g.fillRect(0, 0, w, h);
            g.setPaint(new GradientPaint(0, h * 0.45f, new Color(30, 8, 8, 0), 0, h, new Color(30, 8, 8, 150)));
            g.fillRect(0, 0, w, h);

            int btnTop = cta.getParent() == null ? h - 90 : SwingUtilities.convertPoint(cta.getParent(), cta.getLocation(), this).y;
            int maxW = Math.min(w - 2 * MARGIN, 720);
            Font hf = new Font("Georgia", Font.BOLD, w >= 1000 ? 56 : 42), sf = F_BODY.deriveFont(16f);
            Font tf = new Font("Georgia", Font.ITALIC, w >= 1000 ? 26 : 21).deriveFont(Map.of(java.awt.font.TextAttribute.TRACKING, 0.08f));
            FontMetrics hm = g.getFontMetrics(hf), sm = g.getFontMetrics(sf), tm = g.getFontMetrics(tf);
            List<String> lines = wrap(HEADLINE, hm, maxW);
            int slide = Math.round((1f - fade) * 14);               // gentle upward drift while fading in
            int subBase = btnTop - 22 + slide;
            int tagBase = subBase;                                  // "Baked Fresh Daily" sits directly under the name, above the button
            int lastBase = tagBase - tm.getAscent() - 32;
            int headBase = lastBase - (lines.size() - 1) * (hm.getHeight() + 2);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0.01f, fade)));
            // status badge (pill) above the headline
            g.setFont(F_SMALL.deriveFont(Font.BOLD, 13f));
            FontMetrics bm = g.getFontMetrics();
            int bw = bm.stringWidth(statusText) + 44, bh = 28, by = headBase - hm.getAscent() - bh - 14;
            g.setColor(new Color(255, 255, 255, 46)); g.fillRoundRect(MARGIN, by, bw, bh, bh, bh);
            g.setColor(new Color(255, 255, 255, 110)); g.drawRoundRect(MARGIN, by, bw, bh, bh, bh);
            g.setColor(statusColor); g.fillOval(MARGIN + 13, by + (bh - 10) / 2, 10, 10);
            g.setColor(Color.WHITE); g.drawString(statusText, MARGIN + 32, by + (bh + bm.getAscent() - bm.getDescent()) / 2);
            g.setFont(hf);
            for (int i = 0; i < lines.size(); i++) {
                int y = headBase + i * (hm.getHeight() + 2);
                g.setColor(new Color(0, 0, 0, 90)); g.drawString(lines.get(i), MARGIN + 2, y + 2);     // soft shadow
                g.setColor(Color.WHITE); g.drawString(lines.get(i), MARGIN, y);
            }
            g.setColor(C_SOFT); g.fillRoundRect(MARGIN, lastBase + hm.getDescent() + 9, 56, 3, 3, 3);     // short accent rule
            g.setFont(tf);
            g.setColor(new Color(0, 0, 0, 90)); g.drawString(TAGLINE, MARGIN + 1, tagBase + 1);
            g.setColor(blend(C_SOFT, Color.WHITE, 0.45f)); g.drawString(TAGLINE, MARGIN, tagBase);
            g.dispose();
        }
    }

    // =====================================================================
    //  MAIN WINDOW
    // =====================================================================
    private final CardLayout pages = new CardLayout();
    private final JPanel content = new JPanel(pages);
    private boolean adminLoggedIn = false;
    private final Cart cart = new Cart();      // the ONE shared cart: Home highlights, Customer Portal and the header cart pill
    private CustomerPortal customerPortal; private AdminPortal adminPortal;
    private String currentPage = "HOME"; private int currentStep = 0;
    private static final String[] STEP_CRUMBS = {"Menu", "Custom Cake", "Cart", "Track Order"};

    // Right side of the header: shows a "Back to Home" button on the Customer / Admin pages.
    private final CardLayout headerCards = new CardLayout();
    private final JPanel headerRight = new JPanel(headerCards);
    private final RoundButton cartPill = new RoundButton("", C_LIGHT, C_DARK);          // live item count + total, opens the cart
    private final JPanel crumbs = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));     // breadcrumb trail
    private final JPanel adminTools = new JPanel(new GridBagLayout());                   // "Admin Mode Active" + low-stock shortcut

    public MicroBakeryApp() {
        super("Crumb & Whisk");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1200, 780); setMinimumSize(new Dimension(1050, 680)); setExtendedState(JFrame.MAXIMIZED_BOTH);
        setLayout(new BorderLayout());
        JPanel north = new JPanel(new BorderLayout());
        north.add(buildNavBar(), BorderLayout.NORTH); north.add(buildCrumbBar(), BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
        customerPortal = new CustomerPortal(cart, s -> { currentStep = s; refreshBreadcrumb(); });
        adminPortal = new AdminPortal(() -> { adminLoggedIn = false; navigate("HOME"); });
        content.add(new LandingPage(this::navigate, this::openCustomer), "HOME");
        content.add(customerPortal, "CUSTOMER");
        content.add(adminPortal, "ADMIN");
        add(content, BorderLayout.CENTER);
        cart.addListener(this::refreshCartPill);
        Bakery.get().addListener(this::refreshAdminTools);       // the low-stock count follows the real inventory
        refreshBreadcrumb(); refreshCartPill(); refreshAdminTools();
        cartPill.setVisible(false);                  // the app opens on the homepage, which has no cart button
    }

    private JPanel buildNavBar() {
        JPanel bar = new GradientPanel(new BorderLayout(), C_DARK, C_ACCENT);
        bar.setBorder(new EmptyBorder(8, 18, 8, 18));
        bar.setPreferredSize(new Dimension(0, 60));      // fixed header height
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0)); left.setOpaque(false);
        left.add(new Logo(44)); left.add(label("Crumb & Whisk", F_H2, C_LIGHT));

        headerRight.setOpaque(false);
        JPanel emptyHeader = new JPanel(); emptyHeader.setOpaque(false);
        headerRight.add(emptyHeader, "EMPTY");
        headerRight.add(buildBackButton(), "BACK");      // shown on the Customer / Admin pages

        cartPill.setIcon(new NavIcon(NavIcon.Type.CART)); cartPill.setIconTextGap(8);
        cartPill.setToolTipText("Open your cart and checkout");
        cartPill.addActionListener(e -> openCustomer(2));
        JPanel east = new JPanel(new GridBagLayout()); east.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints(); gc.insets = new Insets(0, 10, 0, 0);
        east.add(cartPill, gc); east.add(headerRight, gc);

        bar.add(left, BorderLayout.WEST); bar.add(east, BorderLayout.EAST);
        return bar;
    }

    /** Thin bar under the header: breadcrumbs on the left, admin badge + low-stock shortcut (admin only) on the right. */
    private JPanel crumbBar;                                    // the whole bar under the header (hidden on the homepage)
    private JPanel buildCrumbBar() {
        JPanel bar = new JPanel(new BorderLayout()); bar.setBackground(C_LIGHT); crumbBar = bar;
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, C_SOFT), new EmptyBorder(0, 18, 0, 18)));
        bar.setPreferredSize(new Dimension(0, 38));
        crumbs.setOpaque(false);
        JPanel crumbWrap = new JPanel(new GridBagLayout()); crumbWrap.setOpaque(false);
        GridBagConstraints w = new GridBagConstraints(); w.anchor = GridBagConstraints.WEST; crumbWrap.add(crumbs, w);

        RoundedPanel badge = new RoundedPanel(new FlowLayout(FlowLayout.LEFT, 7, 4), C_DARK, 16);
        StatusDot dot = new StatusDot(new Color(0x66D17A)); JPanel dotWrap = new JPanel(new GridBagLayout()); dotWrap.setOpaque(false); dotWrap.add(dot);
        badge.add(dotWrap); badge.add(label("Admin Mode Active", F_SMALL.deriveFont(Font.BOLD), Color.WHITE));
        GridBagConstraints t = new GridBagConstraints(); t.insets = new Insets(0, 0, 0, 10);
        adminTools.setOpaque(false); adminTools.add(badge, t);

        bar.add(crumbWrap, BorderLayout.WEST); bar.add(adminTools, BorderLayout.EAST);
        return bar;
    }

    private JLabel crumb(String text, Runnable action) {
        boolean current = action == null;
        JLabel l = label(text, current ? F_SMALL.deriveFont(Font.BOLD, 13f) : F_SMALL.deriveFont(13f), C_DARK);
        if (!current) {
            l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            l.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { action.run(); }
                @Override public void mouseEntered(MouseEvent e) { l.setText("<html><u>" + text.replace("&", "&amp;") + "</u></html>"); }
                @Override public void mouseExited(MouseEvent e) { l.setText(text); }
            });
        }
        return l;
    }
    /** Rebuilds "Crumb & Whisk > Customer Portal > Cart" for the page and step that are showing. */
    private void refreshBreadcrumb() {
        crumbs.removeAll();
        boolean home = currentPage.equals("HOME"), customer = currentPage.equals("CUSTOMER");
        crumbs.add(crumb("Crumb & Whisk", home ? null : () -> navigate("HOME")));
        if (home) { crumbs.add(label("\u203A", F_BODY, C_GREY)); crumbs.add(crumb("Home", null)); }
        else if (customer) {
            crumbs.add(label("\u203A", F_BODY, C_GREY)); crumbs.add(crumb("Customer Portal", currentStep == 0 ? null : () -> openCustomer(0)));
            if (currentStep != 0) { crumbs.add(label("\u203A", F_BODY, C_GREY)); crumbs.add(crumb(STEP_CRUMBS[currentStep], null)); }
            else { crumbs.add(label("\u203A", F_BODY, C_GREY)); crumbs.add(crumb(STEP_CRUMBS[0], null)); }
        } else { crumbs.add(label("\u203A", F_BODY, C_GREY)); crumbs.add(crumb("Admin Portal", null)); }
        crumbs.revalidate(); crumbs.repaint();
        // The homepage shows no breadcrumb; the Customer and Admin portals keep it.
        crumbs.setVisible(!home);
        crumbBar.setVisible(!home);
    }
    private void refreshCartPill() {
        int n = cart.count();
        cartPill.setText(n + (n == 1 ? " item" : " items"));
        cartPill.revalidate(); cartPill.repaint();
    }
    private void refreshAdminTools() {
        adminTools.setVisible(adminLoggedIn && "ADMIN".equals(currentPage));       // the "Admin Mode Active" badge belongs to the Admin Portal only
        int low = Bakery.get().getLowStock().size();
    }

    /** Professional bakery info for the header: phone, email, opening hours, social links (placeholders). */
    private JPanel buildContactInfo() {
        JPanel row = new JPanel(); row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.add(infoBlock("Call us", CONTACT_PHONE));       row.add(Box.createHorizontalStrut(26));
        row.add(infoBlock("Email", CONTACT_EMAIL));         row.add(Box.createHorizontalStrut(26));
        row.add(infoBlock("Opening hours", CONTACT_HOURS)); row.add(Box.createHorizontalStrut(26));
        row.add(infoBlock("Follow us", CONTACT_SOCIAL));
        JPanel wrap = new JPanel(new GridBagLayout()); wrap.setOpaque(false);   // GridBagLayout centres the row vertically
        wrap.add(row);
        return wrap;
    }

    /** A small two-line block: tiny caption on top, the value underneath. */
    private JPanel infoBlock(String caption, String value) {
        JPanel p = new JPanel(); p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel cap = label(caption.toUpperCase(), F_SMALL.deriveFont(10f), C_SOFT);
        JLabel val = label(value, F_SMALL.deriveFont(Font.BOLD), C_LIGHT);
        cap.setAlignmentX(Component.LEFT_ALIGNMENT); val.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(cap); p.add(Box.createVerticalStrut(2)); p.add(val);
        p.setAlignmentY(Component.CENTER_ALIGNMENT);
        return p;
    }

    /** Only shown on the Customer / Admin pages, so the customer is never stuck there. */
    private JPanel buildBackButton() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        p.setOpaque(false);
        RoundButton b = new RoundButton("Back to Home", C_SOFT, C_DARK);          // light button: stands out on the header gradient
        b.addActionListener(e -> navigate("HOME"));
        p.add(b);
        return p;
    }

    void navigate(String page) {
        if (page.equals("ADMIN") && !adminLoggedIn && !login()) return;   // the admin portal always needs the password
        pages.show(content, page); currentPage = page;
        headerCards.show(headerRight, page.equals("HOME") ? "EMPTY" : "BACK");
        cartPill.setVisible(!page.equals("HOME") && !page.equals("ADMIN"));      // no cart button on the homepage or in the Admin Portal; the Customer Portal keeps it
        refreshBreadcrumb(); refreshAdminTools();
    }
    /** Opens the Customer Portal on a step: 0 Menu, 1 Custom Cake, 2 Cart & Checkout, 3 Track Order. */
    void openCustomer(int step) { customerPortal.showStep(step); navigate("CUSTOMER"); }

    /**
     * Administrator login. This application has ONE account type (the administrator), protected by the
     * demo password below, so a correct password is the administrator check. Anyone else is denied.
     */
    private boolean login() {
        JPasswordField pf = new JPasswordField(14);
        int r = JOptionPane.showConfirmDialog(this, new Object[]{"Enter administrator password (demo: admin123)", pf},
                "Admin Login", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return false;
        if ("admin123".equals(new String(pf.getPassword()))) { adminLoggedIn = true; return true; }
        JOptionPane.showMessageDialog(this, "Access denied. Only the administrator can open the Admin Portal.",
                "Access denied", JOptionPane.ERROR_MESSAGE);
        return false;
    }

    // =====================================================================
    //  LANDING PAGE
    // =====================================================================
    /** Small rounded product thumbnail: same proportions as the photo (1000 x 535), so the whole picture fills it. */
    static class ThumbImage extends JComponent {
        private final String productName; private final BufferedImage img;
        ThumbImage(BakeryItem p, int height) {
            productName = p.getName();
            img = ImageLoader.load(productName);
            Dimension d = new Dimension((int) Math.round(height * ProductImage.RATIO), height);
            setPreferredSize(d); setMinimumSize(d); setMaximumSize(d);
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int w = getWidth(), h = getHeight();
            g.clip(new java.awt.geom.RoundRectangle2D.Double(0, 0, w, h, 14, 14));   // intersect (not replace) so scrolled-out parts stay hidden
            g.setColor(C_LIGHT); g.fillRect(0, 0, w, h);                    // neutral background when there is no photo
            if (img != null) {
                double s = Math.min((double) w / img.getWidth(), (double) h / img.getHeight());
                int dw = (int) Math.round(img.getWidth() * s), dh = (int) Math.round(img.getHeight() * s);
                if (Math.abs(dw - w) <= 2 && Math.abs(dh - h) <= 2) { dw = w; dh = h; }
                g.drawImage(ImageLoader.fitted(productName, img, dw, dh), (w - dw) / 2, (h - dh) / 2, dw, dh, null);
            }
            g.dispose();
        }
    }

    static class LandingPage extends JPanel {
        private final IntConsumer openStep; private final OperatingHours hours = OperatingHours.load();
        private final HeroBanner hero;
        private final JPanel highlightHolder = new JPanel(new BorderLayout());
        private final JLabel highlightNote = label(" ", F_BODY, C_GREY);

        LandingPage(Consumer<String> nav, IntConsumer openStep) {
            this.openStep = openStep;
            setLayout(new BorderLayout()); setBackground(C_LIGHT);
            hero = new HeroBanner(() -> openStep.accept(0));
            refreshStatus();
            new javax.swing.Timer(30_000, e -> refreshStatus()).start();      // keeps "open / closed" accurate while the app stays open

            JPanel page = new WidthTrackingPanel(new GridBagLayout()); page.setBackground(C_LIGHT);
            GridBagConstraints g = new GridBagConstraints();
            g.gridx = 0; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;

            // --- Today's Featured Highlights (compact strip)
            JPanel hl = new JPanel(new BorderLayout(0, 10)); hl.setOpaque(false); hl.setBorder(new EmptyBorder(22, 40, 4, 40));
            JPanel titles = new JPanel(); titles.setOpaque(false); titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
            JLabel hlTitle = label("Today's Featured Highlights", F_H2.deriveFont(24f), C_DARK); hlTitle.setAlignmentX(LEFT_ALIGNMENT);
            highlightNote.setAlignmentX(LEFT_ALIGNMENT);
            titles.add(hlTitle);                      // heading only: the subtitle line (highlightNote) is no longer shown
            highlightHolder.setOpaque(false);
            hl.add(titles, BorderLayout.NORTH); hl.add(highlightHolder, BorderLayout.CENTER);

            // --- Get Started: the Admin Portal card (customers enter through the hero button / cart pill)
            JPanel portals = new JPanel(); portals.setOpaque(false); portals.setLayout(new BoxLayout(portals, BoxLayout.Y_AXIS));
            portals.setBorder(new EmptyBorder(20, 40, 24, 40));
            JPanel cards = new JPanel(new BorderLayout()); cards.setOpaque(false);
            cards.add(adminBanner("Admin Portal", "Manage orders, inventory, sales reports, profit estimates and product popularity.", "Admin Login", "ADMIN", nav));
            cards.setAlignmentX(CENTER_ALIGNMENT);
            JLabel foot = label("Bakery hours: " + hours.summary(), F_SMALL, C_GREY); foot.setAlignmentX(CENTER_ALIGNMENT);
            portals.add(cards); portals.add(Box.createVerticalStrut(16)); portals.add(foot);

            g.gridy = 0; page.add(hero, g);
            g.gridy = 1; page.add(hl, g);
            g.gridy = 2; page.add(portals, g);
            g.gridy = 3; g.weighty = 1; g.fill = GridBagConstraints.BOTH; JPanel filler = new JPanel(); filler.setOpaque(false); page.add(filler, g);

            JScrollPane sp = new JScrollPane(page, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            sp.setBorder(null); sp.getViewport().setBackground(C_LIGHT); sp.getVerticalScrollBar().setUnitIncrement(20);
            add(sp, BorderLayout.CENTER);

            rebuildHighlights();
            Bakery.get().addListener(this::rebuildHighlights);                // best-sellers follow real orders as they are placed
        }

        private void refreshStatus() {
            LocalDateTime now = LocalDateTime.now();
            Color dot = !hours.isAccepting() ? new Color(0xFFB74D) : hours.isOpen(now) ? new Color(0x66D17A) : new Color(0xEF7C7C);
            hero.setStatus(hours.statusText(now), dot);
        }

        /** Top three products by units sold in the saved orders (the same figures as the Popularity tab). Nothing is invented. */
        private void rebuildHighlights() {
            Map<String, Integer> sold = Bakery.get().unitsSold();
            List<BakeryItem> top = Bakery.get().getMenu().stream()
                    .filter(p -> sold.getOrDefault(p.getName(), 0) > 0)
                    .sorted((a, b) -> sold.get(b.getName()) - sold.get(a.getName()))
                    .limit(3).collect(Collectors.toList());
            highlightHolder.removeAll();
            RoundedPanel strip = new RoundedPanel(new GridLayout(1, 3, 0, 0), Color.WHITE, 22);
            strip.setBorder(new EmptyBorder(12, 8, 12, 8));
            if (top.isEmpty()) {
                highlightNote.setText("Our top sellers, ranked from real orders.");
                strip = new RoundedPanel(new BorderLayout(), Color.WHITE, 22); strip.setBorder(new EmptyBorder(18, 24, 18, 24));
                strip.add(label("Best-sellers will appear here as soon as customers place orders.", F_BODY, C_DARK), BorderLayout.CENTER);
            } else {
                highlightNote.setText("Our " + (top.size() == 1 ? "best-seller" : "top " + top.size() + " best-sellers") + ", ranked from real order history.");
                for (int i = 0; i < 3; i++) strip.add(i < top.size() ? highlightItem(top.get(i), i + 1, i > 0) : emptySlot(i > 0));
            }
            highlightHolder.add(strip, BorderLayout.CENTER);
            highlightHolder.revalidate(); highlightHolder.repaint();
        }

        private JPanel emptySlot(boolean divider) {
            JPanel p = new JPanel(); p.setOpaque(false);
            if (divider) p.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, C_SOFT));
            return p;
        }

        /** One compact entry: small photo, rank and name. */
        private JPanel highlightItem(BakeryItem p, int rank, boolean divider) {
            JPanel item = new JPanel(new BorderLayout(12, 0)); item.setOpaque(false);
            item.setBorder(divider ? BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, C_SOFT), new EmptyBorder(0, 14, 0, 6))
                    : new EmptyBorder(0, 14, 0, 6));
            JPanel text = new JPanel(); text.setOpaque(false); text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            JLabel rk = label("#" + rank, F_SMALL.deriveFont(Font.BOLD), C_ACCENT);
            JLabel nm = label(p.getName(), F_BOLD, C_DARK); nm.setToolTipText(p.getName());
            for (JLabel l : new JLabel[]{rk, nm}) l.setAlignmentX(LEFT_ALIGNMENT);
            text.add(rk); text.add(nm);                                  // prices are not shown in this section
            JPanel textWrap = new JPanel(new GridBagLayout()); textWrap.setOpaque(false);       // vertically centred next to the photo
            GridBagConstraints c = new GridBagConstraints(); c.weightx = 1; c.anchor = GridBagConstraints.WEST; c.fill = GridBagConstraints.HORIZONTAL;
            textWrap.add(text, c);
            JPanel photo = new JPanel(new GridBagLayout()); photo.setOpaque(false); photo.add(new ThumbImage(p, 60));
            item.add(photo, BorderLayout.WEST); item.add(textWrap, BorderLayout.CENTER);
            return item;
        }

        /** Admin Portal card: full width of the highlights strip above it, text on the left and the login button on the right. */
        private JPanel adminBanner(String title, String desc, String btn, String target, Consumer<String> nav) {
            RoundedPanel p = new RoundedPanel(new BorderLayout(24, 0), Color.WHITE, 28);
            p.setBorder(new EmptyBorder(20, 28, 20, 28));
            JPanel text = new JPanel(); text.setOpaque(false); text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            JLabel t = label(title, F_H2, C_DARK);
            t.setAlignmentX(LEFT_ALIGNMENT);
            text.add(Box.createVerticalGlue()); text.add(t); text.add(Box.createVerticalGlue());   // heading only (description removed), centred beside the button
            RoundButton b = primary(btn); b.setPreferredSize(new Dimension(170, 42)); b.addActionListener(e -> nav.accept(target));
            JPanel east = new JPanel(new GridBagLayout()); east.setOpaque(false); east.add(b);   // centred vertically
            p.add(text, BorderLayout.CENTER); p.add(east, BorderLayout.EAST);
            hoverEffect(p, p);
            return p;
        }
        private JPanel portalCard(String title, String desc, String btn, String target, Consumer<String> nav) {
            RoundedPanel p = new RoundedPanel(new BorderLayout(0, 12), Color.WHITE, 28);
            p.setBorder(new EmptyBorder(22, 26, 22, 26)); p.setPreferredSize(new Dimension(340, 205));
            JLabel d = label("<html>" + desc + "</html>", F_BODY, C_DARK);
            RoundButton b = primary(btn); b.addActionListener(e -> nav.accept(target));
            p.add(label(title, F_H2, C_DARK), BorderLayout.NORTH); p.add(d, BorderLayout.CENTER); p.add(b, BorderLayout.SOUTH);
            hoverEffect(p, p);
            return p;
        }
    }

    // =====================================================================
    //  CUSTOMER PORTAL (step bar with icons)
    // =====================================================================
    static class CustomerPortal extends JPanel {
        private static final int CART_STEP = 2;
        private final Cart cart;                                    // the single shared cart (owned by the main window)
        private final IntConsumer onStep;
        private final CardLayout steps = new CardLayout();
        private final JPanel body = new JPanel(steps);
        private final String[] names = {"Menu", "Custom Cake", "Cart & Checkout", "Track Order"};
        private final NavIcon.Type[] iconTypes = {NavIcon.Type.DASHBOARD, NavIcon.Type.CAKE_SLICE, NavIcon.Type.CART, NavIcon.Type.PACKAGE_SEARCH};
        private final RoundButton[] stepBtns = new RoundButton[4];
        private final RoundButton back = secondary("Back"), next = primary("Next");
        private final CheckoutPanel checkout;
        private final TrackPanel track;
        private MenuPanel menu;
        private JPanel dietHolder;
        private final Dimension fullStep;
        private int current = 0;

        CustomerPortal(Cart cart, IntConsumer onStep) {
            this.cart = cart; this.onStep = onStep;
            setLayout(new BorderLayout()); setBackground(C_LIGHT);
            JPanel bar = new JPanel(new GridBagLayout()); bar.setBackground(C_LIGHT);   // GridBagLayout centres the button row vertically
            JPanel stepRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0)); stepRow.setOpaque(false);
            for (int i = 0; i < 4; i++) {
                final int idx = i;
                stepBtns[i] = secondary(names[i]);
                stepBtns[i].setIcon(new NavIcon(iconTypes[i]));     // icon comes before the title
                stepBtns[i].setIconTextGap(8);
                stepBtns[i].addActionListener(e -> go(idx));
                stepRow.add(stepBtns[i]);
            }
            // The badge on the Cart button is read straight from the cart every time the cart changes.
            stepBtns[CART_STEP].setBadge(cart.count());
            cart.addListener(() -> stepBtns[CART_STEP].setBadge(cart.count()));
            // All four buttons get the same size so they line up evenly; the bar itself is a fixed 60 px tall.
            Dimension stepSize = new Dimension(0, 0);
            for (RoundButton b : stepBtns) {
                Dimension d = b.getPreferredSize();
                stepSize.width = Math.max(stepSize.width, d.width); stepSize.height = Math.max(stepSize.height, d.height);
            }
            for (RoundButton b : stepBtns) b.setPreferredSize(stepSize);
            fullStep = new Dimension(stepSize);
            GridBagConstraints sc = new GridBagConstraints(); sc.anchor = GridBagConstraints.WEST; sc.weightx = 1;   // pinned to the left edge
            bar.setBorder(new EmptyBorder(0, 10, 0, 20));         // + the row's own 10 px = lines up with the page content (20 px)
            bar.add(stepRow, sc); bar.setPreferredSize(new Dimension(0, 60));

            checkout = new CheckoutPanel(cart, this::orderPlaced);
            track = new TrackPanel();
            menu = new MenuPanel(cart);
            // Dietary preference + count sit at the right end of the same row (Menu step only).
            // When the window is narrow the four buttons shrink a little so everything still fits on one line.
            dietHolder = new JPanel(new BorderLayout()); dietHolder.setOpaque(false);
            dietHolder.add(menu.navExtras());
            GridBagConstraints dc = new GridBagConstraints(); dc.anchor = GridBagConstraints.EAST; dc.gridx = 1;
            bar.add(dietHolder, dc);
            bar.addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent e) { fitNavRow(bar); }
            });
            body.setOpaque(false);
            body.add(menu, "0");
            body.add(new CustomCakePanel(cart), "1");
            body.add(checkout, "2");
            body.add(track, "3");
            JPanel foot = new JPanel(new BorderLayout()); foot.setBackground(C_LIGHT); foot.setBorder(new EmptyBorder(3, 20, 3, 20));
            foot.setPreferredSize(new Dimension(0, 44));      // compact footer, 44 px tall
            back.addActionListener(e -> go(Math.max(0, current - 1)));
            next.addActionListener(e -> go(Math.min(3, current + 1)));
            foot.add(back, BorderLayout.WEST); foot.add(next, BorderLayout.EAST);
            add(bar, BorderLayout.NORTH); add(body, BorderLayout.CENTER); add(foot, BorderLayout.SOUTH);
            go(0);
        }
        private void fitNavRow(JPanel bar) {
            boolean showDiet = current == 0;
            menu.setCompactNav(showDiet && bar.getWidth() < 1180);
            int dietW = showDiet ? menu.navExtras().getPreferredSize().width + 16 : 0;
            int avail = bar.getWidth() - 30 - dietW - 3 * 10 - 20;
            // "Cart & Checkout" keeps its full width; the other three share what is left (never wider than normal)
            int cartW = Math.min(fullStep.width, Math.max(204, avail - 3 * 160));
            int w = Math.max(156, Math.min(fullStep.width, (avail - cartW) / 3));
            for (int k = 0; k < 4; k++) stepBtns[k].setPreferredSize(new Dimension(k == CART_STEP ? cartW : w, fullStep.height));
            bar.revalidate();
        }
        void showStep(int i) { go(i); }
        private void go(int i) {
            current = i; steps.show(body, String.valueOf(i)); onStep.accept(i);
            for (int k = 0; k < 4; k++) {
                if (k == i) stepBtns[k].recolor(C_ACCENT, Color.WHITE); else stepBtns[k].recolor(C_SOFT, C_DARK);
            }
            back.setEnabled(i > 0); next.setEnabled(i < 3);
            if (dietHolder != null) { dietHolder.setVisible(i == 0); fitNavRow((JPanel) dietHolder.getParent()); }
        }
        private void orderPlaced(String id) { track.showOrder(id); go(3); }
    }

    // =====================================================================
    //  MENU (product cards with images, category + dietary filters)
    // =====================================================================
    static class MenuPanel extends JPanel {
        private static final int GAP = 16, MIN_CARD_WIDTH = 290, MAX_COLUMNS = 4;
        private final Cart cart;
        private final JPanel grid = new JPanel(new GridLayout(0, 3, GAP, GAP));
        private final JComboBox<Diet> dietBox = new JComboBox<>(Diet.values());
        private final JLabel status = label(" ", F_BOLD, C_DARK);
        private final JLabel cartInfo = label(" ", F_BOLD, C_ACCENT);
        private final JLabel countInfo = label(" ", F_SMALL, C_DARK);
        private final JLabel dietLbl = label("Dietary preference:", F_BOLD, C_DARK);
        private final JLabel dietNote = label(" ", F_SMALL, C_GREY);
        private RoundedPanel dietPill;
        private final List<RoundButton> catButtons = new ArrayList<>();
        private final Map<BakeryItem, CartControl> controls = new HashMap<>();   // the quantity control of every card that is showing
        private final javax.swing.Timer statusTimer;      // clears the "Added ..." message after a few seconds
        private final CardLayout stripCards = new CardLayout();
        private final JPanel catStrip = new JPanel(stripCards);   // holds the category buttons ("SHOW") or an empty spacer ("HIDE")
        private JScrollBar vbar;
        private JScrollPane menuScroll;
        private boolean stripShown = false;
        private Category selected = null;
        private int columns = 3;

        /** The dietary preference dropdown and product count, shown beside the main navigation buttons. */
        JComponent navExtras() { return dietPill; }
        /** Shortens the label when the navigation row is narrow. */
        void setCompactNav(boolean compact) { dietLbl.setText(compact ? "Diet:" : "Dietary preference:"); }

        MenuPanel(Cart cart) {
            this.cart = cart;
            statusTimer = new javax.swing.Timer(3000, e -> status.setText(" "));
            statusTimer.setRepeats(false);
            setLayout(new BorderLayout(0, 10)); setBackground(C_LIGHT); setBorder(new EmptyBorder(6, 20, 6, 20));

            // Row 1: category buttons.   Row 2: dietary filter + number of products shown.
            JPanel cats = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); cats.setOpaque(false);
            Category[] cs = Category.values();
            for (int i = -1; i < cs.length; i++) {
                final Category c = i < 0 ? null : cs[i];
                RoundButton b = secondary(c == null ? "All" : c.toString());
                b.addActionListener(e -> { selected = c; rebuild(); });
                catButtons.add(b); cats.add(b);
            }
            // Dietary preference + product count: a soft pill that the portal places in the main navigation row.
            styleDietCombo(dietBox); dietBox.addActionListener(e -> rebuild());
            dietPill = new RoundedPanel(new FlowLayout(FlowLayout.LEFT, 8, 0), new Color(0xFAF6F6), 22);
            dietPill.setBorder(new EmptyBorder(3, 14, 3, 14));
            dietPill.add(dietLbl); dietPill.add(dietBox);
            hoverEffect(dietPill, dietPill);
            // The category buttons only appear once the customer scrolls down the menu (see updateCategoryStrip).
            // The strip always keeps its height (an empty spacer card is shown instead), so nothing jumps.
            catStrip.setOpaque(false);
            JPanel spacer = new JPanel(); spacer.setOpaque(false);
            catStrip.add(spacer, "HIDE"); catStrip.add(cats, "SHOW");
            stripCards.show(catStrip, "HIDE");
            spacer.setPreferredSize(new Dimension(0, cats.getPreferredSize().height));          // strip keeps its height while hidden
            JPanel top = new JPanel(new BorderLayout()); top.setOpaque(false); top.add(catStrip, BorderLayout.CENTER); top.add(dietNote, BorderLayout.SOUTH);   // category strip only; dietary pill lives in the nav row

            grid.setOpaque(false);
            WidthTrackingPanel wrap = new WidthTrackingPanel(new BorderLayout()); wrap.setOpaque(false);
            wrap.add(grid, BorderLayout.NORTH);
            JScrollPane sp = new JScrollPane(wrap); sp.setBorder(null);
            sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            sp.getViewport().setBackground(C_LIGHT); sp.getVerticalScrollBar().setUnitIncrement(16);
            vbar = sp.getVerticalScrollBar(); menuScroll = sp;
            // The menu always opens at the very top (navigation + dietary controls first); customers scroll down to browse.
            Runnable toTop = () -> SwingUtilities.invokeLater(() -> sp.getViewport().setViewPosition(new Point(0, 0)));
            addHierarchyListener(e -> { if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) toTop.run(); });
            toTop.run();
            vbar.addAdjustmentListener(e -> updateCategoryStrip());
            // number of card columns adapts to the window width
            sp.getViewport().addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent e) { updateColumns(sp.getViewport().getWidth()); }
            });

            JPanel bottom = new JPanel(new BorderLayout()); bottom.setOpaque(false);
            bottom.add(status, BorderLayout.CENTER); bottom.add(cartInfo, BorderLayout.EAST);
            add(top, BorderLayout.NORTH); add(sp, BorderLayout.CENTER); add(bottom, BorderLayout.SOUTH);

            cartInfo.setText(cartText(cart));
            // always reflects the real cart: the summary text AND every card's "+" / quantity control
            cart.addListener(() -> {
                cartInfo.setText(cartText(cart));
                for (CartControl control : controls.values()) control.sync();
            });
            rebuild();
        }

        /**
         * Shows the category buttons once the customer has scrolled down the menu (with a little hysteresis so it
         * never flickers). They also stay visible while a category is selected, or when the list is too short to
         * scroll, so the customer is never left without a way to change category.
         */
        private void updateCategoryStrip() {
            if (vbar == null) return;
            boolean scrollable = vbar.getMaximum() - vbar.getVisibleAmount() > 0;
            boolean show = vbar.getValue() > (stripShown ? 20 : 80) || selected != null || !scrollable;
            if (show == stripShown) return;
            stripShown = show;
            stripCards.show(catStrip, show ? "SHOW" : "HIDE");
        }

        private void updateColumns(int width) {
            int n = Math.max(1, Math.min(MAX_COLUMNS, (width + GAP) / (MIN_CARD_WIDTH + GAP)));
            if (n != columns) {
                columns = n; grid.setLayout(new GridLayout(0, columns, GAP, GAP));
                grid.revalidate(); grid.repaint();
            }
        }

        private void rebuild() {
            Category[] cs = Category.values();
            for (int i = 0; i < catButtons.size(); i++) {
                Category c = i == 0 ? null : cs[i - 1];
                if (c == selected) catButtons.get(i).recolor(C_ACCENT, Color.WHITE); else catButtons.get(i).recolor(C_SOFT, C_DARK);
            }
            grid.removeAll();
            controls.clear();                                          // the old cards are gone; their controls must not be updated any more
            Diet d = (Diet) dietBox.getSelectedItem();
            int shown = 0;
            for (BakeryItem p : Bakery.get().getMenu()) {              // one reusable method builds every card
                if (selected != null && p.getCategory() != selected) continue;
                if (!p.supports(d)) continue;
                grid.add(createProductCard(p)); shown++;
            }
            String note = d == Diet.GLUTEN_FREE ? " - made without gluten ingredients; our kitchen also handles wheat"
                    : d == Diet.SUGAR_FREE ? " - no added sugar" : "";
            countInfo.setText(shown + (shown == 1 ? " product" : " products"));
            dietNote.setText(note.isEmpty() ? " " : note.substring(3));     // the caveat stays visible, just under the category strip
            dietNote.setVisible(!note.isEmpty());
            grid.revalidate(); grid.repaint();
            scrollToTop();                                             // category or dietary change: start the new list at the top
            updateCategoryStrip();
            SwingUtilities.invokeLater(this::updateCategoryStrip);     // again once the new list has been laid out
        }

        /** Resets the menu's vertical scroll position to the very top (again after layout, when the new list has its final height). */
        private void scrollToTop() {
            if (menuScroll == null) return;
            menuScroll.getViewport().setViewPosition(new Point(0, 0));
            SwingUtilities.invokeLater(() -> {
                menuScroll.getViewport().setViewPosition(new Point(0, 0));
                menuScroll.getVerticalScrollBar().setValue(0);
            });
        }

        /**
         * The cart control on a menu card. It shows a round "+" button while the product is not in the cart, and an inline
         * [bin] [-] qty [+] stepper once it is. It owns no quantity of its own: every click goes to the shared Cart, and
         * sync() (called by the cart listener) reads the real quantity back, so menu, cart pill, badge and checkout never disagree.
         */
        private final class CartControl extends JPanel {
            private final BakeryItem product;
            private final CardLayout states = new CardLayout();
            private final JLabel qtyLabel = label("1", F_BOLD.deriveFont(15f), C_DARK);

            CartControl(BakeryItem product) {
                this.product = product;
                setLayout(states); setOpaque(false);
                String name = product.getName();

                CircleIconButton addBtn = new CircleIconButton(CircleIconButton.Kind.PLUS, true, 40, "Add " + name + " to cart");
                addBtn.addActionListener(e -> increase(true));
                JPanel addWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0)); addWrap.setOpaque(false); addWrap.add(addBtn);

                CircleIconButton trash = new CircleIconButton(CircleIconButton.Kind.TRASH, false, 34, "Remove " + name + " from cart");
                CircleIconButton minus = new CircleIconButton(CircleIconButton.Kind.MINUS, false, 34, "One fewer");
                CircleIconButton plus = new CircleIconButton(CircleIconButton.Kind.PLUS, true, 34, "One more");
                trash.addActionListener(e -> requestRemove());
                minus.addActionListener(e -> decrease());
                plus.addActionListener(e -> increase(false));
                qtyLabel.setHorizontalAlignment(SwingConstants.CENTER);
                qtyLabel.setPreferredSize(new Dimension(30, 34));
                JPanel stepper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 3)); stepper.setOpaque(false);
                stepper.add(trash); stepper.add(minus); stepper.add(qtyLabel); stepper.add(plus);

                add(addWrap, "ADD"); add(stepper, "QTY");
                sync();
            }


            /** Re-reads the real cart quantity and shows the matching state. */
            void sync() {
                int q = cart.qtyOf(product);
                qtyLabel.setText(String.valueOf(q));
                states.show(this, q > 0 ? "QTY" : "ADD");
            }
            private void increase(boolean announce) {
                cart.add(product, 1);                                    // the SAME shared cart; merges with an existing row
                if (announce) status.setText("Added " + product.getName() + " to your cart (" + cart.qtyOf(product) + " in cart).");
                else status.setText(product.getName() + ": " + cart.qtyOf(product) + " in your cart.");
                statusTimer.restart();
            }
            /** Minus: one fewer; at quantity 1 the item would reach zero, so the same confirmation as the bin is shown. */
            private void decrease() {
                int idx = cart.indexOf(product);
                if (idx < 0) return;
                if (cart.getItems().get(idx).getQty() <= 1) { requestRemove(); return; }
                cart.change(idx, -1);
                status.setText(product.getName() + ": " + cart.qtyOf(product) + " in your cart.");
                statusTimer.restart();
            }
            private void requestRemove() {
                int idx = cart.indexOf(product);
                if (idx < 0) return;
                int qty = cart.getItems().get(idx).getQty();
                if (!confirmRemove(MenuPanel.this, product.getName(), qty)) return;   // Cancel: nothing changes
                int now = cart.indexOf(product);                                      // look the line up again after the dialog
                if (now < 0) return;
                cart.remove(now);
                status.setText("Removed " + product.getName() + " from your cart.");
                statusTimer.restart();}}

        /** Builds ONE product card: image, name, description, dietary info, price and the "+" / quantity control. */
        private JPanel createProductCard(BakeryItem p) {
            RoundedPanel card = new RoundedPanel(new BorderLayout(0, 8), Color.WHITE, 20);
            card.setBorder(new EmptyBorder(12, 14, 14, 14));

            JPanel info = new JPanel(); info.setOpaque(false); info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
            JLabel productName = label(p.getName(), F_BOLD, C_DARK);       // plain text (an HTML width box rendered off-position)
            productName.setHorizontalAlignment(SwingConstants.LEFT);
            productName.setAlignmentX(Component.LEFT_ALIGNMENT);
            info.add(productName);
            JTextArea description = new JTextArea(p.getShortDescription() + " (" + p.getUnit() + ")");
            description.setRows(3); description.setColumns(28);
            description.setLineWrap(true); description.setWrapStyleWord(true);
            description.setEditable(false); description.setFocusable(false);
            ((javax.swing.text.DefaultCaret) description.getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);   // never scroll the menu to the last card
            description.setOpaque(false); description.setBorder(null);
            description.setFont(F_SMALL); description.setForeground(C_TEXT);
            description.setAlignmentX(Component.LEFT_ALIGNMENT);        // same left edge as the name and the price
            info.add(description);

            // One click on "+" = exactly one unit. After that the same spot shows the - / quantity / + / bin controls.
            CartControl control = new CartControl(p);
            controls.put(p, control);
            JLabel price = label(rs(p.getPrice()), F_H2, C_ACCENT);
            JPanel south = new JPanel(new BorderLayout(8, 0)); south.setOpaque(false);
            south.setBorder(new EmptyBorder(4, 0, 0, 0));
            south.add(price, BorderLayout.WEST); south.add(control, BorderLayout.EAST);

            card.add(new ProductImage(p), BorderLayout.NORTH);
            card.add(info, BorderLayout.CENTER);
            card.add(south, BorderLayout.SOUTH);
            addCardHover(card, card);
            return card;
        }

        private void addCardHover(Component component, RoundedPanel card) {
            component.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { card.setHover(true); }
                @Override public void mouseExited(MouseEvent e) {
                    Point point = SwingUtilities.convertPoint(component, e.getPoint(), card);
                    if (!card.contains(point)) card.setHover(false);
                }
            });
            if (component instanceof Container)
                for (Component child : ((Container) component).getComponents()) addCardHover(child, card);
        }
    }

    // =====================================================================
    //  CUSTOM CAKE BUILDER - VISUAL COMPONENTS (drawing only, no business logic)
    // =====================================================================
    /** Colours and small Java2D illustrations for cake options. Unknown option names fall back to a neutral look. */
    static class OptionArt {
        static Color flavour(String n) {
            switch (n) {
                case "Vanilla": return new Color(0xF0DFA8);
                case "Chocolate": return new Color(0x6B4129);
                case "Red Velvet": return new Color(0xB3213F);
                case "Butterscotch": return new Color(0xD99A3D);
                case "Coffee": return new Color(0x93653F);
                case "Lemon": return new Color(0xF2D84A);
                default: return Color.getHSBColor((Math.abs(n.hashCode()) % 360) / 360f, 0.35f, 0.85f);
            }
        }
        static Color frosting(String n) {
            switch (n) {
                case "Buttercream": return new Color(0xF7E4A6);
                case "Whipped Cream": return new Color(0xFEFCF8);
                case "Cream Cheese": return new Color(0xF3E5D0);
                case "Chocolate Ganache": return new Color(0x4D2B1C);
                case "Coconut Cream": return new Color(0xFAF2E2);
                case "Fondant": return new Color(0xF6B9CE);
                case "Stevia Glaze": return new Color(0xDCEBC6);
                default: return Color.getHSBColor((Math.abs(n.hashCode()) % 360) / 360f, 0.25f, 0.97f);
            }
        }
        private static Color shade(Color c, float f) {
            return new Color(Math.max(0, Math.min(255, Math.round(c.getRed() * f))), Math.max(0, Math.min(255, Math.round(c.getGreen() * f))), Math.max(0, Math.min(255, Math.round(c.getBlue() * f))));
        }
        private static Graphics2D aa(Graphics2D g) { Graphics2D c = (Graphics2D) g.create(); c.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); return c; }

        /** A 3-D cake block in the flavour colour (icon for flavour cards). */
        static void flavourIcon(Graphics2D g0, Color c, int x, int y, int s) {
            Graphics2D g = aa(g0); g.translate(x, y); g.scale(s / 44.0, s / 44.0);
            Color fill = blend(c, new Color(0xFFF6E5), 0.65f);
            g.setColor(shade(c, 0.78f)); g.fillPolygon(new int[]{30, 40, 40, 30}, new int[]{18, 11, 29, 36}, 4);       // right face
            g.setColor(blend(c, Color.WHITE, 0.35f)); g.fillPolygon(new int[]{5, 30, 40, 15}, new int[]{18, 18, 11, 11}, 4); // top face
            g.setColor(c); g.fillRect(5, 18, 25, 18);                                                                  // front
            g.setColor(fill); g.fillRect(5, 23, 25, 3); g.fillRect(5, 30, 25, 3);                                      // filling layers
            g.setColor(new Color(0, 0, 0, 45)); g.drawRect(5, 18, 25, 18);
            g.dispose();
        }
        /** Round colour swatch for flavour / frosting tiles: glossy disc, with sponge layers or a piped swirl. */
        static void swatch(Graphics2D g0, Color c, int x, int y, int s, boolean frosting) {
            Graphics2D g = aa(g0); g.translate(x, y); g.scale(s / 54.0, s / 54.0);
            g.setColor(new Color(0, 0, 0, 28)); g.fillOval(4, 6, 48, 46);                                         // soft drop shadow
            java.awt.geom.Ellipse2D.Double disc = new java.awt.geom.Ellipse2D.Double(3, 3, 48, 48);
            g.setPaint(new RadialGradientPaint(new java.awt.geom.Point2D.Double(19, 17), 44f, new float[]{0f, 1f}, new Color[]{blend(c, Color.WHITE, 0.38f), shade(c, 0.82f)}));
            g.fill(disc);
            Shape old = g.getClip(); g.clip(disc);
            if (frosting) {                                                                                         // piped swirl lines
                g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.setColor(new Color(255, 255, 255, 120));
                g.draw(new java.awt.geom.Arc2D.Double(11, 14, 32, 20, 200, 250, java.awt.geom.Arc2D.OPEN));
                g.draw(new java.awt.geom.Arc2D.Double(17, 20, 20, 12, 20, 260, java.awt.geom.Arc2D.OPEN));
                g.setColor(new Color(0, 0, 0, 30)); g.draw(new java.awt.geom.Arc2D.Double(11, 18, 32, 20, 220, 200, java.awt.geom.Arc2D.OPEN));
            } else {                                                                                                // sponge layers with cream filling
                Color cream = blend(c, new Color(0xFFF6E5), 0.7f); g.setColor(cream); g.fillRect(0, 20, 60, 4); g.fillRect(0, 31, 60, 4);
                g.setColor(new Color(0, 0, 0, 22)); g.fillRect(0, 24, 60, 1); g.fillRect(0, 35, 60, 1);
            }
            g.setClip(old);
            g.setColor(new Color(255, 255, 255, 150)); g.fill(new java.awt.geom.Ellipse2D.Double(13, 9, 14, 8));    // gloss
            g.setColor(new Color(0, 0, 0, 55)); g.setStroke(new BasicStroke(1f)); g.draw(disc);
            g.dispose();
        }
        /** A piped frosting swirl (icon for frosting cards). */
        static void frostingIcon(Graphics2D g0, Color c, int x, int y, int s) {
            Graphics2D g = aa(g0); g.translate(x, y); g.scale(s / 44.0, s / 44.0);
            Color edge = new Color(0, 0, 0, 55);
            int[][] t = {{6, 28, 32, 12}, {11, 18, 22, 11}, {16, 9, 12, 10}};                                        // x, y, w, h of three stacked ellipses
            for (int[] e : t) {
                g.setPaint(new GradientPaint(e[0], e[1], blend(c, Color.WHITE, 0.45f), e[0], e[1] + e[3], c));
                g.fillOval(e[0], e[1], e[2], e[3]); g.setColor(edge); g.drawOval(e[0], e[1], e[2], e[3]);
            }
            g.setColor(c); g.fillPolygon(new int[]{19, 25, 22}, new int[]{11, 11, 4}, 3); g.setColor(edge); g.drawPolygon(new int[]{19, 25, 22}, new int[]{11, 11, 4}, 3);
            g.dispose();
        }
        /** A cake silhouette whose width follows the size factor (icon for size cards). */
        static void sizeIcon(Graphics2D g0, int x, int y, int s, double factor) {
            Graphics2D g = aa(g0); g.translate(x, y); g.scale(s / 44.0, s / 44.0);
            double w = 20 + 16 * Math.min(1.0, factor / 2.0) ; double cx = 22;
            g.setColor(C_SOFT); g.fill(new java.awt.geom.RoundRectangle2D.Double(cx - w / 2, 20, w, 16, 6, 6));
            g.setColor(C_ACCENT); g.fill(new java.awt.geom.RoundRectangle2D.Double(cx - w / 2, 14, w, 9, 6, 6));
            if (factor >= 1.5) { g.setColor(C_SOFT); g.fill(new java.awt.geom.RoundRectangle2D.Double(cx - w * 0.3, 7, w * 0.6, 9, 5, 5)); }
            g.setColor(C_DARK); g.fillOval(22 - 2, factor >= 1.5 ? 2 : 8, 4, 4);
            g.dispose();
        }
        /** Small illustration of a topping (icon for topping cards). */
        static void toppingIcon(Graphics2D g0, String name, int x, int y, int s) {
            Graphics2D g = aa(g0); g.translate(x, y); g.scale(s / 44.0, s / 44.0);
            g.setColor(new Color(0xF1EAEA)); g.fillRoundRect(2, 2, 40, 40, 12, 12);
            switch (name) {
                case "Fresh Fruits": berry(g, 15, 24, 8); berry(g, 29, 21, 8); berry(g, 22, 31, 7); break;
                case "Chocolate Chips": for (int[] p : new int[][]{{13, 16}, {27, 14}, {20, 27}, {32, 29}, {12, 32}}) { g.setColor(new Color(0x4B2A1A)); g.fillOval(p[0], p[1], 9, 6); } break;
                case "Sprinkles": { Color[] cs = {new Color(0xF44336), new Color(0xFFC107), new Color(0x4CAF50), new Color(0x2196F3), new Color(0xE91E63)};
                    g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int[][] p = {{11, 14, 17, 10}, {25, 12, 29, 17}, {14, 25, 21, 22}, {27, 27, 33, 24}, {16, 34, 23, 33}, {30, 35, 34, 31}};
                    for (int i = 0; i < p.length; i++) { g.setColor(cs[i % cs.length]); g.drawLine(p[i][0], p[i][1], p[i][2], p[i][3]); } break; }
                case "Crushed Nuts": for (int[] p : new int[][]{{11, 16}, {24, 13}, {18, 26}, {30, 26}, {13, 33}}) { g.setColor(new Color(0xC69C6D)); g.fillPolygon(new int[]{p[0], p[0] + 8, p[0] + 7, p[0] + 1}, new int[]{p[1] + 2, p[1], p[1] + 7, p[1] + 6}, 4); } break;
                case "Caramel Drizzle": g.setColor(new Color(0xD98E2B)); g.setStroke(new BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    Path2D.Double p = new Path2D.Double(); p.moveTo(8, 18); p.curveTo(16, 8, 20, 28, 28, 16); p.curveTo(32, 10, 34, 18, 36, 14); g.draw(p);
                    g.drawLine(16, 22, 16, 31); g.drawLine(27, 20, 27, 34); g.fillOval(14, 30, 5, 5); g.fillOval(25, 33, 5, 5); break;
                case "Edible Flowers": flower(g, 22, 22, 9, new Color(0xF8BBD0)); break;
                default: g.setColor(C_ACCENT); g.fillOval(15, 15, 14, 14);
            }
            g.dispose();
        }
        static void berry(Graphics2D g, double cx, double cy, double r) {
            g.setPaint(new GradientPaint((float) (cx - r), (float) (cy - r), new Color(0xE23A55), (float) (cx + r), (float) (cy + r), new Color(0x9E1B32)));
            g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
            g.setColor(new Color(255, 255, 255, 120)); g.fill(new Ellipse2D.Double(cx - r * 0.55, cy - r * 0.6, r * 0.5, r * 0.4));
            g.setColor(new Color(0x2E7D32)); g.fillPolygon(new int[]{(int) cx - 2, (int) cx + 3, (int) cx}, new int[]{(int) (cy - r), (int) (cy - r), (int) (cy - r - r * 0.5)}, 3);
        }
        static void flower(Graphics2D g, double cx, double cy, double r, Color petal) {
            for (int i = 0; i < 5; i++) {
                double a = Math.toRadians(72 * i - 90);
                g.setColor(petal); g.fill(new Ellipse2D.Double(cx + Math.cos(a) * r * 0.75 - r * 0.5, cy + Math.sin(a) * r * 0.75 - r * 0.5, r, r));
            }
            g.setColor(new Color(0xFFC107)); g.fill(new Ellipse2D.Double(cx - r * 0.35, cy - r * 0.35, r * 0.7, r * 0.7));
        }
    }

    /** White card with a soft layered shadow. Pad = inner padding. Purely visual container. */
    static class SoftCard extends JPanel {
        SoftCard(LayoutManager lm, int pad) { super(lm); setOpaque(false); setBorder(new EmptyBorder(pad + 2, pad + 6, pad + 9, pad + 6)); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight(), x = 4, y = 1, cw = w - 8, ch = h - 9;
            for (int i = 6; i >= 1; i--) {                                        // layered, slightly offset translucent shapes = soft shadow
                g.setColor(new Color(0x4E, 0x20, 0x20, 7 + (6 - i) * 2));
                g.fillRoundRect(x - i + 1, y + 3 - i / 2 + 1, cw + 2 * i - 2, ch + i, 22 + i, 22 + i);
            }
            g.setColor(Color.WHITE); g.fillRoundRect(x, y, cw, ch, 22, 22);
            g.setColor(new Color(0xB9, 0xA0, 0xA0, 120)); g.drawRoundRect(x, y, cw - 1, ch - 1, 22, 22);
            g.dispose(); super.paintComponent(g0);
        }
    }

    /** Selectable tile: art on top, title and price under it. It only reports clicks; the owner decides what a click does. */
    static class TileCard extends JComponent {
        interface Art { void paint(Graphics2D g, int x, int y, int size); }
        private final String title, sub; private final Art art; private final String photoKey;     // photoKey: e.g. "flavour vanilla" -> Images/flavour-vanilla.jpg (null = line art only)
        private final java.util.function.BooleanSupplier selected, enabled; private final Runnable onClick;
        private final boolean toggle;
        private float hover = 0f, pick = 0f; private boolean over = false;
        private final javax.swing.Timer anim;
        TileCard(String title, String sub, Art art, boolean toggle, java.util.function.BooleanSupplier selected, java.util.function.BooleanSupplier enabled, Runnable onClick) {
            this(title, sub, art, null, toggle, selected, enabled, onClick);
        }
        /** Same tile, but shows the photo "Images/<photoKey>.jpg|.jpeg|.png" when it exists; otherwise the Art illustration is the fallback. */
        TileCard(String title, String sub, Art art, String photoKey, boolean toggle, java.util.function.BooleanSupplier selected, java.util.function.BooleanSupplier enabled, Runnable onClick) {
            this.photoKey = photoKey; this.title = title; this.sub = sub; this.art = art; this.toggle = toggle; this.selected = selected; this.enabled = enabled; this.onClick = onClick;
            pick = selected.getAsBoolean() ? 1f : 0f;
            anim = new javax.swing.Timer(16, e -> {
                float t = over ? 1f : 0f; hover += (t - hover) * 0.3f;
                float sTarget = this.selected.getAsBoolean() ? 1f : 0f; pick += (sTarget - pick) * 0.3f;
                boolean done = Math.abs(t - hover) < 0.02f && Math.abs(sTarget - pick) < 0.02f;
                if (done) { hover = t; pick = sTarget; ((javax.swing.Timer) e.getSource()).stop(); }
                repaint();
            });
            setPreferredSize(new Dimension(128, 124)); setMinimumSize(new Dimension(100, 110));
            setFocusable(true); ToolTipManager.sharedInstance().registerComponent(this);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { over = enabled.getAsBoolean(); anim.start(); setCursor(Cursor.getPredefinedCursor(enabled.getAsBoolean() ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR)); }
                @Override public void mouseExited(MouseEvent e) { over = false; anim.start(); }
                @Override public void mouseClicked(MouseEvent e) { requestFocusInWindow(); fire(); }
            });
            addFocusListener(new FocusAdapter() { @Override public void focusGained(FocusEvent e) { repaint(); } @Override public void focusLost(FocusEvent e) { repaint(); } });
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "pick");
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "pick");
            getActionMap().put("pick", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { fire(); } });
        }
        private void fire() { if (enabled.getAsBoolean()) { onClick.run(); anim.start(); repaint(); } }
        /** Lets the selection fade follow changes made elsewhere (e.g. a filter clearing the choice). */
        @Override public void addNotify() { super.addNotify(); anim.start(); }
        @Override public String getToolTipText(MouseEvent e) { return enabled.getAsBoolean() ? null : "Not available for the chosen dietary preference"; }
        /** Draws the photo centred inside the box, scaled to fit (aspect ratio kept: never stretched, never cropped), with softly rounded corners. */
        private void drawPhoto(Graphics2D g, BufferedImage photo, int bx, int by, int bw, int bh, int arc) {
            double sc = Math.min((double) bw / photo.getWidth(), (double) bh / photo.getHeight());
            int pw = Math.max(1, (int) Math.round(photo.getWidth() * sc)), ph = Math.max(1, (int) Math.round(photo.getHeight() * sc));
            int px = bx + (bw - pw) / 2, py = by + (bh - ph) / 2;
            BufferedImage fitted = ImageLoader.fitted(photoKey, photo, pw, ph);
            Graphics2D pg = (Graphics2D) g.create();                  // own copy: clip and interpolation changes never leak into the card
            pg.clip(new java.awt.geom.RoundRectangle2D.Double(px, py, pw, ph, arc, arc));
            pg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            pg.drawImage(fitted, px, py, pw, ph, null);
            pg.dispose();
            g.setColor(new Color(0x4E, 0x20, 0x20, 28)); g.setStroke(new BasicStroke(1f)); g.drawRoundRect(px, py, pw - 1, ph - 1, arc, arc);
        }
        private static String fit(String s, FontMetrics fm, int w) {
            if (fm.stringWidth(s) <= w) return s;
            while (s.length() > 1 && fm.stringWidth(s + "…") > w) s = s.substring(0, s.length() - 1);
            return s + "…";
        }
        @Override protected void paintComponent(Graphics g0) {
            if (Math.abs((selected.getAsBoolean() ? 1f : 0f) - pick) > 0.02f && !anim.isRunning()) anim.start();
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean en = enabled.getAsBoolean(); float sel = pick;
            int w = getWidth(), h = getHeight(), lift = Math.round(2 * hover);
            if (!en) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.42f));
            int x = 2, y = 2 - lift, cw = w - 5, ch = h - 7;
            // shadow grows with hover / selection
            float sh = Math.max(hover * 0.8f, sel);
            for (int i = 4; i >= 1; i--) { g.setColor(new Color(0x4E, 0x20, 0x20, Math.round((4 + sh * 7) * (5 - i) / 2f))); g.fillRoundRect(x - i + 1, y + 2 + lift - i / 2 + 1, cw + 2 * i - 2, ch + i, 20 + i, 20 + i); }
            Color fill = blend(blend(Color.WHITE, C_HOVER, hover), C_TINT, sel);
            g.setColor(fill); g.fillRoundRect(x, y, cw, ch, 18, 18);
            g.setColor(blend(blend(C_SOFT, C_ACCENT, hover * 0.7f), C_ACCENT, sel));
            g.setStroke(new BasicStroke(1f + sel)); g.drawRoundRect(x, y, cw - 1, ch - 1, 18, 18);
            int sz = 54;
            BufferedImage photo = photoKey == null ? null : ImageLoader.load(photoKey);
            int textTop;                                                         // y where the art/photo area ends and the title starts
            if (ch > 125) {                                                      // tall card (photos present): one common photo box, art is centred in it
                int boxX = x + 10, boxY = y + 9, boxW = cw - 20, boxH = ch - 9 - 46;
                if (photo != null) drawPhoto(g, photo, boxX, boxY, boxW, boxH, 12);
                else art.paint(g, (w - sz) / 2, boxY + (boxH - sz) / 2, sz);
                textTop = boxY + boxH;
            } else {                                                             // normal card (no photos): exactly the original layout
                if (photo != null) drawPhoto(g, photo, x + 10, y + 10, cw - 20, 58, 10);
                else art.paint(g, (w - sz) / 2, y + 12, sz);
                textTop = y + 12 + sz;
            }
            g.setFont(F_BOLD.deriveFont(13f)); FontMetrics fm = g.getFontMetrics();
            String t = fit(title, fm, cw - 14); g.setColor(C_DARK); g.drawString(t, (w - fm.stringWidth(t)) / 2, textTop + 18);
            g.setFont(F_SMALL); FontMetrics sm = g.getFontMetrics();
            String st = fit(sub, sm, cw - 14); g.setColor(sel > 0.5f ? C_DARK : C_GREY); g.drawString(st, (w - sm.stringWidth(st)) / 2, textTop + 18 + sm.getAscent() + 2);
            int bx = x + cw - 24, by = y + 7;
            if (sel > 0.05f) {                                                   // check badge, scales in
                int d = Math.round(18 * Math.min(1f, sel)); int cx = bx + 9, cy = by + 9;
                g.setColor(C_ACCENT); g.fillOval(cx - d / 2, cy - d / 2, d, d);
                if (sel > 0.7f) { g.setColor(Color.WHITE); g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.drawPolyline(new int[]{bx + 4, bx + 8, bx + 14}, new int[]{by + 9, by + 13, by + 5}, 3); }
            } else if (toggle) {                                                 // "+" = tap to add
                g.setColor(blend(C_SOFT, C_ACCENT, hover)); g.setStroke(new BasicStroke(1.5f)); g.drawOval(bx, by, 18, 18);
                g.drawLine(bx + 5, by + 9, bx + 13, by + 9); g.drawLine(bx + 9, by + 5, bx + 9, by + 13);
            }
            if (isFocusOwner()) { g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{3f, 3f}, 0f)); g.setColor(C_DARK); g.drawRoundRect(x + 3, y + 3, cw - 7, ch - 7, 14, 14); }
            g.dispose();
        }
    }

    /** Grid of cards that uses as many columns (1-3) as fit, so cards never get cramped. */
    static class CardGrid extends JPanel {
        private static final int GAP = 12;
        private final int maxCols, MIN_W; private int ROW_H;
        /** Photo cards need a little more height than the line-art cards; the owner switches it once it knows photos exist. */
        void setRowHeight(int h) { if (h != ROW_H) { ROW_H = h; revalidate(); repaint(); } }
        CardGrid(int maxCols, int minW, int rowH) {
            super(null); this.maxCols = maxCols; this.MIN_W = minW; this.ROW_H = rowH; setOpaque(false);
            setLayout(new LayoutManager() {
                private int cols(Container p) { int w = p.getWidth() > 0 ? p.getWidth() : maxCols * MIN_W + (maxCols - 1) * GAP; return Math.max(1, Math.min(maxCols, (w + GAP) / (MIN_W + GAP))); }
                public void addLayoutComponent(String n, Component c) { }
                public void removeLayoutComponent(Component c) { }
                public Dimension preferredLayoutSize(Container p) {
                    int n = p.getComponentCount(), c = cols(p), rows = (n + c - 1) / c, rh = ROW_H;
                    return new Dimension(maxCols * MIN_W + (maxCols - 1) * GAP, rows == 0 ? 0 : rows * rh + (rows - 1) * GAP);
                }
                public Dimension minimumLayoutSize(Container p) { return new Dimension(MIN_W, ROW_H); }
                public void layoutContainer(Container p) {
                    int n = p.getComponentCount(), c = cols(p), cw = (p.getWidth() - (c - 1) * GAP) / c, rh = ROW_H;
                    for (int i = 0; i < n; i++) p.getComponent(i).setBounds((i % c) * (cw + GAP), (i / c) * (rh + GAP), cw, rh);
                }
            });
            addComponentListener(new ComponentAdapter() {
                private int last = -1;
                @Override public void componentResized(ComponentEvent e) {
                    int c = Math.max(1, Math.min(maxCols, (getWidth() + GAP) / (MIN_W + GAP)));
                    if (c != last) { last = c; revalidate(); }
                }
            });
        }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
    }

    /** Small numbered circle used in the section headings. */
    static class NumberBadge extends JComponent {
        private final String n;
        NumberBadge(int n) { this.n = String.valueOf(n); Dimension d = new Dimension(26, 26); setPreferredSize(d); setMinimumSize(d); setMaximumSize(d); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, C_ACCENT, 0, getHeight(), C_DARK)); g.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
            g.setColor(Color.WHITE); g.setFont(F_BOLD.deriveFont(13f)); FontMetrics fm = g.getFontMetrics();
            g.drawString(n, (getWidth() - fm.stringWidth(n)) / 2, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g.dispose();
        }
    }

    /** Rounded dietary-preference chip. It only reports clicks; the owner decides what a click does. */
    static class PillChip extends JComponent {
        private final String text; private final java.util.function.BooleanSupplier selected; private final Runnable onClick;
        private float hover = 0f, pick = 0f; private boolean over = false;
        private final javax.swing.Timer anim;
        PillChip(String text, java.util.function.BooleanSupplier selected, Runnable onClick) {
            this.text = text; this.selected = selected; this.onClick = onClick;
            pick = selected.getAsBoolean() ? 1f : 0f;
            anim = new javax.swing.Timer(16, e -> {
                float t = over ? 1f : 0f; hover += (t - hover) * 0.3f;
                float st = this.selected.getAsBoolean() ? 1f : 0f; pick += (st - pick) * 0.3f;
                if (Math.abs(t - hover) < 0.02f && Math.abs(st - pick) < 0.02f) { hover = t; pick = st; ((javax.swing.Timer) e.getSource()).stop(); }
                repaint();
            });
            setPreferredSize(new Dimension(120, 40)); setMinimumSize(new Dimension(80, 36));
            setFocusable(true); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { over = true; anim.start(); }
                @Override public void mouseExited(MouseEvent e) { over = false; anim.start(); }
                @Override public void mouseClicked(MouseEvent e) { requestFocusInWindow(); fire(); }
            });
            addFocusListener(new FocusAdapter() { @Override public void focusGained(FocusEvent e) { repaint(); } @Override public void focusLost(FocusEvent e) { repaint(); } });
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "pick");
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "pick");
            getActionMap().put("pick", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { fire(); } });
        }
        private void fire() { onClick.run(); anim.start(); repaint(); }
        @Override public void addNotify() { super.addNotify(); anim.start(); }
        @Override protected void paintComponent(Graphics g0) {
            if (Math.abs((selected.getAsBoolean() ? 1f : 0f) - pick) > 0.02f && !anim.isRunning()) anim.start();
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g.setColor(blend(blend(Color.WHITE, C_HOVER, hover), C_ACCENT, pick)); g.fillRoundRect(1, 1, w - 3, h - 3, h, h);
            g.setColor(blend(blend(C_SOFT, C_ACCENT, hover * 0.7f), C_ACCENT, pick)); g.setStroke(new BasicStroke(1.2f)); g.drawRoundRect(1, 1, w - 3, h - 3, h, h);
            g.setFont(F_BOLD.deriveFont(13f)); FontMetrics fm = g.getFontMetrics();
            boolean on = pick > 0.5f; int tx = w / 2 - fm.stringWidth(text) / 2 + (on ? 8 : 0), ty = (h + fm.getAscent() - fm.getDescent()) / 2;
            g.setColor(blend(C_DARK, Color.WHITE, pick)); g.drawString(text, tx, ty);
            if (on) {                                                            // check mark
                g.setColor(Color.WHITE); g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = tx - 15, cy = h / 2; g.drawPolyline(new int[]{cx, cx + 4, cx + 11}, new int[]{cy, cy + 4, cy - 4}, 3);
            }
            if (isFocusOwner()) { g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{3f, 3f}, 0f)); g.setColor(C_DARK); g.drawRoundRect(4, 4, w - 9, h - 9, h - 6, h - 6); }
            g.dispose();
        }
    }

    /** Wine-gradient heading banner at the top of the Custom Cake builder. */
    static class BannerPanel extends JPanel {
        BannerPanel(String title, String subtitle) {
            super(new BorderLayout(0, 4)); setOpaque(false); setBorder(new EmptyBorder(16, 28, 16, 28));
            add(label(title, F_H2.deriveFont(24f), Color.WHITE), BorderLayout.NORTH);
            if (subtitle != null && !subtitle.isEmpty()) add(label(subtitle, F_BODY, new Color(0xF3E8E8)), BorderLayout.CENTER);
        }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int x = 4, y = 1, w = getWidth() - 8, h = getHeight() - 3;
            g.setPaint(new GradientPaint(x, 0, C_DARK, x + w, 0, C_ACCENT)); g.fillRoundRect(x, y, w, h, 22, 22);
            Shape old = g.getClip(); g.clip(new java.awt.geom.RoundRectangle2D.Double(x, y, w, h, 22, 22));
            g.setColor(new Color(255, 255, 255, 18)); g.fillOval(x + w - 150, y - 60, 220, 220); g.fillOval(x + w - 60, y + h - 70, 130, 130);
            g.setClip(old);
            g.setColor(new Color(255, 255, 255, 45)); g.drawRoundRect(x, y, w - 1, h - 1, 22, 22);
            g.dispose(); super.paintComponent(g0);
        }
    }

    // =====================================================================
    //  CUSTOM CAKE BUILDER (+ dietary validation)
    // =====================================================================
    static class CustomCakePanel extends JPanel {
        private static final int MAX_MSG = 40;
        private final Bakery bakery = Bakery.get();
        private final Cart cart;
        private final JComboBox<CakeSize> size = new JComboBox<>();
        private final JComboBox<Diet> diet = new JComboBox<>(Diet.values());
        private final JComboBox<CakeOption> flavour = new JComboBox<>();
        private final JComboBox<CakeOption> frosting = new JComboBox<>();
        private final Map<JCheckBox, CakeOption> toppingBoxes = new LinkedHashMap<>();
        private final JTextField message = new JTextField();
        private final JLabel counter = label("0 / " + MAX_MSG, F_SMALL, C_GREY);
        private final JSpinner qty = new JSpinner(new SpinnerNumberModel(1, 1, 20, 1));
        private final JTextArea summary = new JTextArea();
        private final JLabel totalLbl = label(" ", F_H2, C_ACCENT);
        private final RoundButton addBtn = primary("Add custom cake to cart");
        private boolean updating = false, sizeSelected = false, flavourSelected = false;
        private boolean frostingSelected = false, dietSelected = false;
        // visual layer (display + click forwarding only; the fields above remain the single source of truth)
        private final CardGrid sizeGrid = new CardGrid(4, 118, 124), flavourGrid = new CardGrid(3, 118, 124), frostingGrid = new CardGrid(4, 118, 124), toppingGrid = new CardGrid(3, 150, 124);
        private final List<CakeOption> shownFlavours = new ArrayList<>(), shownFrostings = new ArrayList<>();
        private final JPanel priceRows = new JPanel();
        private final JLabel needLbl = label(" ", F_SMALL, C_GREY);
        private JPanel dietChips;

        CustomCakePanel(Cart cart) {
            this.cart = cart;
            setLayout(new BorderLayout(14, 0)); setBackground(C_LIGHT); setBorder(new EmptyBorder(6, 14, 6, 14));
            for (CakeSize s : bakery.getSizes()) size.addItem(s);
            size.setSelectedIndex(-1);
            diet.setSelectedIndex(-1);
            for (CakeOption t : bakery.getToppings()) {
                JCheckBox cb = new JCheckBox(t.toString()); cb.setOpaque(true); cb.setBackground(Color.WHITE); cb.setFont(F_BODY); cb.setForeground(C_TEXT);
                cb.addActionListener(e -> {
                    cb.setBackground(cb.isSelected() ? C_TINT : Color.WHITE);
                    updateSummary();});
                cb.addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) {
                        if (cb.isEnabled() && !cb.isSelected()) cb.setBackground(C_HOVER);}
                    @Override public void mouseExited(MouseEvent e) {
                        cb.setBackground(cb.isSelected() ? C_TINT : Color.WHITE);}});
                toppingBoxes.put(cb, t);}
            styleCombo(size); styleCombo(diet); styleCombo(flavour); styleCombo(frosting); styleField(message); qty.setFont(F_BODY);
            ((AbstractDocument) message.getDocument()).setDocumentFilter(new LimitFilter(MAX_MSG));
            onTextChange(message, () -> { counter.setText(message.getText().length() + " / " + MAX_MSG); updateSummary(); });
            size.addActionListener(e -> {
                if (!updating) sizeSelected = true;
                updateSummary();});
            flavour.addActionListener(e -> {
                if (!updating) flavourSelected = true;
                updateSummary();});
            frosting.setBorder(BorderFactory.createLineBorder(C_SOFT));
            frosting.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { frosting.setBorder(BorderFactory.createLineBorder(C_ACCENT, 2)); }
                @Override public void mouseExited(MouseEvent e) {
                    frosting.setBorder(BorderFactory.createLineBorder(frostingSelected ? C_ACCENT : C_SOFT));}});
            frosting.addActionListener(e -> {
                if (!updating) {
                    frostingSelected = true;
                    frosting.setBorder(BorderFactory.createLineBorder(C_ACCENT));
                }
                updateSummary();
            });
            qty.addChangeListener(e -> updateSummary());
            diet.addActionListener(e -> {
                if (!updating) dietSelected = true;
                refreshOptions();
            });

            // ---- Visual builder. The combo boxes and check boxes above stay the single source of truth (pricing, diet filtering
            // and validation all read them); the cards below only display them and forward clicks to them.
            for (CakeSize sz : bakery.getSizes())
                sizeGrid.add(new TileCard(sz.getName(), rs(sz.getPrice()), (g, x, y, z) -> OptionArt.sizeIcon(g, x, y, z, sz.getFactor()), ImageLoader.optionKey("size", sz.getName()), false,
                        () -> sizeSelected && size.getSelectedItem() == sz, () -> true, () -> size.setSelectedItem(sz)));
            for (Map.Entry<JCheckBox, CakeOption> e : toppingBoxes.entrySet()) {
                JCheckBox cb = e.getKey(); CakeOption t = e.getValue();
                toppingGrid.add(new TileCard(t.getName(), t.getExtra() > 0 ? "+" + rs(t.getExtra()) : "Included",
                        (g, x, y, z) -> OptionArt.toppingIcon(g, t.getName(), x, y, z), ImageLoader.optionKey("topping", t.getName()), true, cb::isSelected, cb::isEnabled, cb::doClick));
            }
            sizeGrid.setRowHeight(anyPhoto("size", sizeNames()) ? PHOTO_ROW_H : 124);          // photo cards get a little more height
            toppingGrid.setRowHeight(anyPhoto("topping", new ArrayList<>(toppingBoxes.values())) ? PHOTO_ROW_H : 124);
            // ---- Layout: shadowed section cards on the left, itemised price + total on the right
            JPanel msg = new JPanel(new BorderLayout(10, 0)); msg.setOpaque(false);
            message.setPreferredSize(new Dimension(100, 38)); message.setToolTipText("Optional text written on the cake (up to " + MAX_MSG + " characters)");
            msg.add(message, BorderLayout.CENTER); msg.add(counter, BorderLayout.EAST);
            // quantity stepper: [-] [spinner] [+]. The spinner is still the one source of truth (same 1-20 range).
            qty.setUI(new javax.swing.plaf.basic.BasicSpinnerUI() {          // hide the tiny arrows; the large buttons do the job
                @Override protected Component createNextButton() { JButton b = new JButton(); b.setPreferredSize(new Dimension(0, 0)); b.setFocusable(false); return b; }
                @Override protected Component createPreviousButton() { JButton b = new JButton(); b.setPreferredSize(new Dimension(0, 0)); b.setFocusable(false); return b; }
            });
            JSpinner.DefaultEditor qe = (JSpinner.DefaultEditor) qty.getEditor();
            qe.getTextField().setHorizontalAlignment(JTextField.CENTER); qe.getTextField().setFont(F_BOLD.deriveFont(15f));
            qty.setBorder(null); qe.getTextField().setBorder(BorderFactory.createLineBorder(C_SOFT)); qty.setPreferredSize(new Dimension(64, 38));
            RoundButton minus = secondary("−"), plus = secondary("+");
            for (RoundButton b : new RoundButton[]{minus, plus}) { b.setPreferredSize(new Dimension(46, 38)); b.setFont(F_BOLD.deriveFont(16f)); b.setBorder(new EmptyBorder(0, 0, 0, 0)); }
            minus.setToolTipText("One fewer"); plus.setToolTipText("One more");
            minus.addActionListener(e -> { Object v = qty.getModel().getPreviousValue(); if (v != null) qty.setValue(v); });
            plus.addActionListener(e -> { Object v = qty.getModel().getNextValue(); if (v != null) qty.setValue(v); });
            JPanel qtyWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); qtyWrap.setOpaque(false);
            qtyWrap.add(minus); qtyWrap.add(qty); qtyWrap.add(plus);
            JPanel dietBody = new JPanel(new BorderLayout(0, 6)); dietBody.setOpaque(false);
            JPanel dietRow = new JPanel(new BorderLayout()); dietRow.setOpaque(false);
            diet.setRenderer(new DefaultListCellRenderer() {
                @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSel, boolean focus) {
                    super.getListCellRendererComponent(list, value, index, isSel, focus);
                    setBorder(new EmptyBorder(4, 8, 4, 8));
                    if (value == null) { setText("Select a dietary preference"); setForeground(C_GREY); setFont(F_BODY.deriveFont(Font.ITALIC)); }
                    return this;
                }
            });
            diet.setPreferredSize(new Dimension(320, 38)); dietRow.add(diet, BorderLayout.WEST);
            // dietary preference as chips (the combo box above stays the single source of truth and is not shown)
            dietChips = new JPanel(new GridLayout(2, 3, 10, 10)); dietChips.setOpaque(false);
            for (Diet dv : Diet.values())
                dietChips.add(new PillChip(dv.toString(), () -> dietSelected && diet.getSelectedItem() == dv, () -> diet.setSelectedItem(dv)));
            dietBody.add(dietChips, BorderLayout.NORTH);
            dietBody.add(label("Only options the bakery can prepare for the chosen dietary preference are available.", F_SMALL, C_GREY), BorderLayout.CENTER);
            JPanel finishing = new JPanel(new GridBagLayout()); finishing.setOpaque(false);
            addRow(finishing, 0, "Message on cake", msg);
            addRow(finishing, 1, "Quantity", qtyWrap);

            JPanel stack = new JPanel(); stack.setOpaque(false); stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
            JPanel bannerWrap = new JPanel(new BorderLayout()); bannerWrap.setOpaque(false); bannerWrap.setAlignmentX(LEFT_ALIGNMENT);
            bannerWrap.setBorder(new EmptyBorder(2, 0, 6, 0));
            bannerWrap.add(new BannerPanel("Design Your Custom Cake", ""));
            stack.add(bannerWrap);
            stack.add(section(1, "Cake size", "", sizeGrid));
            stack.add(section(2, "Dietary preference", "optional", dietBody));
            stack.add(section(3, "Flavour", "choose one", flavourGrid));
            stack.add(section(4, "Frosting", "choose one", frostingGrid));
            stack.add(section(5, "Toppings", "choose any", toppingGrid));
            stack.add(section(6, "Finishing touches", "", finishing));
            WidthTrackingPanel formHost = new WidthTrackingPanel(new BorderLayout()); formHost.setOpaque(false); formHost.add(stack, BorderLayout.NORTH);
            JScrollPane fs = new JScrollPane(formHost, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            fs.setBorder(null); fs.getViewport().setOpaque(false); fs.setOpaque(false); fs.getVerticalScrollBar().setUnitIncrement(18);

            // right side: itemised price summary (scrolls if the window is short); the total and the button stay in view
            priceRows.setOpaque(false); priceRows.setLayout(new BoxLayout(priceRows, BoxLayout.Y_AXIS));
            SoftCard priceBox = new SoftCard(new BorderLayout(0, 10), 12);
            JPanel pHead = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); pHead.setOpaque(false);
            pHead.add(label("Your custom cake", F_H2.deriveFont(18f), C_DARK));
            JPanel pBody = new JPanel(new BorderLayout(0, 6)); pBody.setOpaque(false);
            JSeparator hs = new JSeparator(); hs.setForeground(C_SOFT);
            JPanel pTop = new JPanel(new BorderLayout(0, 8)); pTop.setOpaque(false); pTop.add(pHead, BorderLayout.NORTH); pTop.add(hs, BorderLayout.SOUTH);
            pBody.add(priceRows, BorderLayout.NORTH); pBody.add(needLbl, BorderLayout.CENTER);
            priceBox.add(pTop, BorderLayout.NORTH); priceBox.add(pBody, BorderLayout.CENTER);
            WidthTrackingPanel col = new WidthTrackingPanel(new BorderLayout()); col.setOpaque(false);
            col.add(priceBox, BorderLayout.NORTH);
            JScrollPane cs = new JScrollPane(col, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            cs.setBorder(null); cs.getViewport().setOpaque(false); cs.setOpaque(false); cs.getVerticalScrollBar().setUnitIncrement(16);

            summary.setEditable(false); summary.setLineWrap(true); summary.setWrapStyleWord(true);   // text version, kept up to date but not shown
            addBtn.addActionListener(e -> addToCart());
            addBtn.setEnabled(false); addBtn.setPreferredSize(new Dimension(100, 46));
            SoftCard bottom = new SoftCard(new GridLayout(2, 1, 0, 10), 10);
            bottom.add(totalLbl); bottom.add(addBtn);
            JPanel right = new JPanel(new BorderLayout(0, 6)); right.setOpaque(false); right.setPreferredSize(new Dimension(380, 100));
            right.add(cs, BorderLayout.CENTER); right.add(bottom, BorderLayout.SOUTH);
            add(fs, BorderLayout.CENTER); add(right, BorderLayout.EAST);

            refreshOptions();
        }

        /** Re-filters flavours, frostings and toppings so only diet-compatible options are offered. */
        private void refreshOptions() {
            updating = true;
            Diet selectedDiet = (Diet) diet.getSelectedItem();
            Diet d = selectedDiet == null ? Diet.NONE : selectedDiet;
            CakeOption oldFl = (CakeOption) flavour.getSelectedItem(), oldFr = (CakeOption) frosting.getSelectedItem();
            flavour.removeAllItems(); frosting.removeAllItems();
            for (CakeOption o : bakery.getFlavours()) if (o.supports(d)) flavour.addItem(o);
            for (CakeOption o : bakery.getFrostings()) if (o.supports(d)) frosting.addItem(o);
            if (oldFl != null && oldFl.supports(d)) flavour.setSelectedItem(oldFl);
            else {
                flavour.setSelectedIndex(-1);
                if (oldFl != null) flavourSelected = false;
            }
            if (oldFr != null && oldFr.supports(d)) frosting.setSelectedItem(oldFr);
            else {
                frosting.setSelectedIndex(-1);
                if (oldFr != null) frostingSelected = false;
            }
            for (Map.Entry<JCheckBox, CakeOption> e : toppingBoxes.entrySet()) {
                boolean ok = e.getValue().supports(d);
                e.getKey().setEnabled(ok); if (!ok) e.getKey().setSelected(false);
                e.getKey().setBackground(e.getKey().isSelected() ? C_TINT : Color.WHITE);
            }
            updating = false; updateSummary();
        }

        /** Builds the cake from the form, or returns null if the combination is not allowed. */
        private CustomCake build() {
            CakeSize s = (CakeSize) size.getSelectedItem(); CakeOption fl = (CakeOption) flavour.getSelectedItem();
            CakeOption fr = (CakeOption) frosting.getSelectedItem();
            Diet selectedDiet = (Diet) diet.getSelectedItem();
            Diet d = selectedDiet == null ? Diet.NONE : selectedDiet;
            if (!sizeSelected || !flavourSelected || !frostingSelected
                    || s == null || fl == null || fr == null || !fl.supports(d) || !fr.supports(d)) return null;
            List<CakeOption> tops = new ArrayList<>();
            for (Map.Entry<JCheckBox, CakeOption> e : toppingBoxes.entrySet())
                if (e.getKey().isSelected()) { if (!e.getValue().supports(d)) return null; tops.add(e.getValue()); }
            return new CustomCake(s, fl, fr, tops, d, message.getText().trim());
        }

        private void updateSummary() {
            if (updating) return;
            CakeSize selectedSize = (CakeSize) size.getSelectedItem();
            CakeOption selectedFlavour = (CakeOption) flavour.getSelectedItem();
            CakeOption selectedFrosting = (CakeOption) frosting.getSelectedItem();
            List<CakeOption> selectedToppings = new ArrayList<>();
            for (Map.Entry<JCheckBox, CakeOption> e : toppingBoxes.entrySet())
                if (e.getKey().isSelected()) selectedToppings.add(e.getValue());
            String cakeMessage = message.getText().trim();

            CustomCake c = build();
            addBtn.setEnabled(c != null || !sizeSelected);      // stays clickable without a size so the customer sees the "Please select cake size." message

            StringBuilder details = new StringBuilder();
            if (sizeSelected && selectedSize != null)
                details.append("Size: ").append(selectedSize.getName()).append("\n");
            if (flavourSelected && selectedFlavour != null)
                details.append("Flavour: ").append(selectedFlavour.getName()).append("\n");
            if (frostingSelected && selectedFrosting != null)
                details.append("Frosting: ").append(selectedFrosting.getName()).append("\n");
            if (!selectedToppings.isEmpty())
                details.append("Toppings: ").append(selectedToppings.stream().map(CakeOption::getName).collect(Collectors.joining(", "))).append("\n");
            if (dietSelected && diet.getSelectedItem() != null)
                details.append("Dietary preference: ").append(diet.getSelectedItem()).append("\n");
            if (!cakeMessage.isEmpty())
                details.append("Message: \"").append(cakeMessage).append("\"\n");

            int q = (Integer) qty.getValue();
            if (c != null) details.append("\nPrice per cake: ").append(rs(c.getPrice()));
            summary.setText(details.toString().trim());
            totalLbl.setText(c != null ? "Total: " + rs(c.getPrice() * q) : " ");
            syncVisuals(c, q);
        }

        // ---------- visual layer helpers (display only) ----------
        private JPanel section(int n, String title, String hint, JComponent body) {
            SoftCard p = new SoftCard(new BorderLayout(0, 10), 12); p.setAlignmentX(LEFT_ALIGNMENT);
            JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0)); head.setOpaque(false);
            head.add(new NumberBadge(n)); head.add(label(title, F_H2.deriveFont(18f), C_DARK));
            if (!hint.isEmpty()) head.add(label(hint, F_SMALL, C_GREY));
            JSeparator sep = new JSeparator(); sep.setForeground(new Color(0xE3D8D8));
            JPanel top = new JPanel(new BorderLayout(0, 8)); top.setOpaque(false); top.add(head, BorderLayout.NORTH); top.add(sep, BorderLayout.SOUTH);
            p.add(top, BorderLayout.NORTH); p.add(body, BorderLayout.CENTER);
            JPanel wrap = new JPanel(new BorderLayout()); wrap.setOpaque(false); wrap.setAlignmentX(LEFT_ALIGNMENT); wrap.add(p);
            return wrap;
        }

        private static final int PHOTO_ROW_H = 152;
        /** True when at least one of these options has a photo in the Images folder (then the cards get a little more height). */
        private static boolean anyPhoto(String section, List<CakeOption> opts) {
            for (CakeOption o : opts) if (ImageLoader.load(ImageLoader.optionKey(section, o.getName())) != null) return true;
            return false;
        }
        private List<CakeOption> sizeNames() {
            List<CakeOption> l = new ArrayList<>();
            for (CakeSize sz : bakery.getSizes()) l.add(new CakeOption(sz.getName(), 0));
            return l;
        }
        /** Rebuilds the flavour / frosting cards when the diet filter changed the offered options, and repaints the selection state. */
        private void rebuildCards(CardGrid grid, JComboBox<CakeOption> combo, List<CakeOption> shown, boolean isFlavour) {
            List<CakeOption> now = new ArrayList<>();
            for (int i = 0; i < combo.getItemCount(); i++) now.add(combo.getItemAt(i));
            if (!now.equals(shown)) {
                shown.clear(); shown.addAll(now); grid.removeAll();
                for (CakeOption o : now) {
                    TileCard.Art art = isFlavour ? (g, x, y, z) -> OptionArt.swatch(g, OptionArt.flavour(o.getName()), x, y, z, false)
                            : (g, x, y, z) -> OptionArt.swatch(g, OptionArt.frosting(o.getName()), x, y, z, true);
                    grid.add(new TileCard(o.getName(), o.getExtra() > 0 ? "+" + rs(o.getExtra()) : "Included", art, ImageLoader.optionKey(isFlavour ? "flavour" : "frosting", o.getName()), false,
                            () -> (isFlavour ? flavourSelected : frostingSelected) && combo.getSelectedItem() == o, () -> true, () -> combo.setSelectedItem(o)));
                }
                grid.setRowHeight(anyPhoto(isFlavour ? "flavour" : "frosting", now) ? PHOTO_ROW_H : 124);
                grid.revalidate();
            }
            grid.repaint();
        }

        private void syncVisuals(CustomCake c, int q) {
            rebuildCards(flavourGrid, flavour, shownFlavours, true);
            rebuildCards(frostingGrid, frosting, shownFrostings, false);
            sizeGrid.repaint(); toppingGrid.repaint();
            CakeSize sz = (CakeSize) size.getSelectedItem(); CakeOption fl = (CakeOption) flavour.getSelectedItem(), fr = (CakeOption) frosting.getSelectedItem();
            boolean hasSize = sizeSelected && sz != null, hasFl = flavourSelected && fl != null, hasFr = frostingSelected && fr != null;
            List<CakeOption> tops = new ArrayList<>();
            for (Map.Entry<JCheckBox, CakeOption> e : toppingBoxes.entrySet()) if (e.getKey().isSelected()) tops.add(e.getValue());
            // itemised price summary: the same figures the cake is priced from (size + flavour + frosting + toppings)
            priceRows.removeAll();
            selRow("Size", hasSize ? sz.getName() : null, hasSize ? rs(sz.getPrice()) : null);
            selRow("Flavour", hasFl ? fl.getName() : null, hasFl ? extraText(fl) : null);
            selRow("Frosting", hasFr ? fr.getName() : null, hasFr ? extraText(fr) : null);
            if (tops.isEmpty()) selRow("Toppings", "None", null);
            for (CakeOption t : tops) selRow("Topping", t.getName(), extraText(t));
            if (dietSelected && diet.getSelectedItem() != null) selRow("Dietary", diet.getSelectedItem().toString(), null);
            String m = message.getText().trim();
            if (!m.isEmpty()) selRow("Message", "\"" + m + "\"", null);
            if (dietChips != null) dietChips.repaint();
            JSeparator sep = new JSeparator(); sep.setForeground(C_SOFT); sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8)); priceRows.add(Box.createVerticalStrut(4)); priceRows.add(sep); priceRows.add(Box.createVerticalStrut(4));
            double sofar = (hasSize ? sz.getPrice() : 0) + (hasFl ? fl.getExtra() : 0) + (hasFr ? fr.getExtra() : 0);
            for (CakeOption t : tops) sofar += t.getExtra();
            boolean any = hasSize || hasFl || hasFr || !tops.isEmpty();
            sumRow(c != null ? "Price per cake" : "Price so far", c != null ? rs(c.getPrice()) : any ? rs(sofar) : "\u2014", true);
            sumRow("Quantity", "\u00D7 " + q, false);
            List<String> missing = new ArrayList<>();
            if (!hasSize) missing.add("size"); if (!hasFl) missing.add("flavour"); if (!hasFr) missing.add("frosting");
            needLbl.setText(c != null || missing.isEmpty() ? " " : "Still needed: " + String.join(", ", missing));
            priceRows.revalidate(); priceRows.repaint();
        }
        private static String extraText(CakeOption o) { return o.getExtra() > 0 ? "+" + rs(o.getExtra()) : "Included"; }
        private void selRow(String caption, String value, String price) {
            JPanel row = new JPanel(new BorderLayout(8, 0)); row.setOpaque(false); row.setBorder(new EmptyBorder(2, 0, 2, 0));
            JLabel cap = label(caption, F_SMALL, C_GREY); cap.setPreferredSize(new Dimension(70, 18));
            JLabel val = value != null ? label(value, F_BOLD, C_DARK) : label("Not chosen yet", F_SMALL.deriveFont(Font.ITALIC), C_GREY);
            row.add(cap, BorderLayout.WEST); row.add(val, BorderLayout.CENTER);
            if (price != null) row.add(label(price, F_BODY, C_DARK), BorderLayout.EAST);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height)); row.setAlignmentX(LEFT_ALIGNMENT);
            priceRows.add(row);
        }
        private void sumRow(String caption, String value, boolean big) {
            JPanel row = new JPanel(new BorderLayout(8, 0)); row.setOpaque(false); row.setBorder(new EmptyBorder(3, 0, 3, 0));
            row.add(label(caption, big ? F_BOLD : F_SMALL, big ? C_DARK : C_GREY), BorderLayout.WEST);
            row.add(label(value, big ? F_H2.deriveFont(17f) : F_BOLD, big ? C_ACCENT : C_DARK), BorderLayout.EAST);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height)); row.setAlignmentX(LEFT_ALIGNMENT);
            priceRows.add(row);
        }

        private void addToCart() {
            if (!sizeSelected || size.getSelectedItem() == null) { warn(this, "Please select cake size."); return; }     // cake size is mandatory
            CustomCake c = build();
            if (c == null) { warn(this, "That combination cannot be prepared. Please adjust your options."); return; }
            int q = (Integer) qty.getValue(); cart.add(c, q);
            info(this, q + " x " + c.getName() + " added to your cart (" + rs(c.getPrice() * q) + ").");
        }
    }

    // =====================================================================
    //  CART, CHECKOUT & PAYMENT METHODS
    // =====================================================================
    static class CheckoutPanel extends JPanel {
        private final Cart cart; private final Consumer<String> onPlaced;
        private final JPanel itemsPanel = new JPanel();
        private final JLabel cartTitle = label("Your cart", F_H2, C_DARK);
        private final JLabel subtotalLbl = label(" ", F_BODY, C_DARK);
        private final JLabel deliveryFeeLbl = label(" ", F_BODY, C_DARK);
        private final JLabel totalLbl = label(" ", F_H2, C_ACCENT);
        private final JTextField name = new JTextField(), phone = new JTextField(), address = new JTextField();
        private final JRadioButton pickup = new JRadioButton("Pickup", true), delivery = new JRadioButton("Delivery");
        private final JRadioButton cod = new JRadioButton("Cash on Delivery (COD)", false), online = new JRadioButton("Online Payment", false);
        private final ButtonGroup paymentGroup = new ButtonGroup();

        CheckoutPanel(Cart cart, Consumer<String> onPlaced) {
            this.cart = cart; this.onPlaced = onPlaced;
            setLayout(new BorderLayout(16, 0)); setBackground(C_LIGHT); setBorder(new EmptyBorder(6, 20, 6, 20));

            RoundedPanel left = new RoundedPanel(new BorderLayout(0, 10), Color.WHITE, 22); left.setBorder(new EmptyBorder(14, 16, 14, 16));
            RoundButton clear = secondary("Clear cart");
            clear.addActionListener(e -> cart.clear());
            JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); btns.setOpaque(false);
            btns.add(clear);
            JPanel cartHeader = new JPanel(new BorderLayout()); cartHeader.setOpaque(false);
            cartHeader.add(cartTitle, BorderLayout.WEST); cartHeader.add(btns, BorderLayout.EAST);
            itemsPanel.setOpaque(false);
            itemsPanel.setLayout(new BoxLayout(itemsPanel, BoxLayout.Y_AXIS));
            JScrollPane itemScroll = new JScrollPane(itemsPanel);
            itemScroll.setBorder(null); itemScroll.getViewport().setOpaque(false);
            itemScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            itemScroll.getVerticalScrollBar().setUnitIncrement(16);
            left.add(cartHeader, BorderLayout.NORTH);
            left.add(itemScroll, BorderLayout.CENTER);
            JPanel totals = new JPanel();
            totals.setOpaque(false);
            totals.setLayout(new BoxLayout(totals, BoxLayout.Y_AXIS));
            subtotalLbl.setAlignmentX(Component.RIGHT_ALIGNMENT);
            deliveryFeeLbl.setAlignmentX(Component.RIGHT_ALIGNMENT);
            totalLbl.setAlignmentX(Component.RIGHT_ALIGNMENT);
            totals.add(subtotalLbl);
            totals.add(deliveryFeeLbl);
            totals.add(totalLbl);
            JPanel lb = new JPanel(new BorderLayout()); lb.setOpaque(false); lb.add(totals, BorderLayout.EAST);
            left.add(lb, BorderLayout.SOUTH);

            RoundedPanel right = new RoundedPanel(new GridBagLayout(), Color.WHITE, 22);
            right.setBorder(new EmptyBorder(14, 16, 14, 16)); right.setPreferredSize(new Dimension(390, 100));
            styleField(name); styleField(phone); styleField(address); address.setEnabled(false);
            ButtonGroup g1 = new ButtonGroup(); g1.add(pickup); g1.add(delivery);
            paymentGroup.add(cod); paymentGroup.add(online);
            for (JRadioButton rb : new JRadioButton[]{pickup, delivery, cod, online}) { rb.setOpaque(false); rb.setFont(F_BODY); rb.setForeground(C_TEXT); }
            JPanel typeP = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0)); typeP.setOpaque(false); typeP.add(pickup); typeP.add(delivery);
            JPanel payP = new JPanel(); payP.setOpaque(false); payP.setLayout(new BoxLayout(payP, BoxLayout.Y_AXIS));
            payP.add(cod); payP.add(online);
            // Changing the order type re-checks the payment rules straight away.
            pickup.addActionListener(e -> { address.setEnabled(false); applyOrderTypeRules(); refresh(); });
            delivery.addActionListener(e -> { address.setEnabled(true); applyOrderTypeRules(); refresh(); });
            ((AbstractDocument) phone.getDocument()).setDocumentFilter(new LocalPhoneFilter());
            JLabel dialCode = label(PHONE_PREFIX, F_BOLD, C_DARK);
            dialCode.setOpaque(true); dialCode.setBackground(C_LIGHT);
            dialCode.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(C_SOFT), new EmptyBorder(6, 8, 6, 8)));
            JPanel phoneRow = new JPanel(new BorderLayout(6, 0)); phoneRow.setOpaque(false);
            phoneRow.add(dialCode, BorderLayout.WEST); phoneRow.add(phone, BorderLayout.CENTER);
            addRow(right, 0, "Full name", name); addRow(right, 1, "Phone", phoneRow); addRow(right, 2, "Order type", typeP);
            addRow(right, 3, "Address", address); addRow(right, 4, "Payment", payP);
            GridBagConstraints g = new GridBagConstraints(); g.gridx = 0; g.gridy = 5; g.gridwidth = 2; g.fill = GridBagConstraints.HORIZONTAL; g.insets = new Insets(4, 4, 10, 4);
            RoundButton place = primary("Place Order"); place.addActionListener(e -> placeOrder());
            g.weighty = 1; g.anchor = GridBagConstraints.NORTH; right.add(place, g);
            add(left, BorderLayout.CENTER); add(right, BorderLayout.EAST);

            applyOrderTypeRules(); refresh();
            cart.addListener(this::refresh);        // the table, title and total always follow the real cart
        }

        /** COD is available for delivery only; demo online payment is available for pickup or delivery. */
        private void applyOrderTypeRules() {
            boolean isDelivery = delivery.isSelected();
            online.setEnabled(true);
            cod.setEnabled(isDelivery);
            if (!isDelivery && cod.isSelected()) paymentGroup.clearSelection();
            if (isDelivery && !cod.isSelected() && !online.isSelected()) cod.setSelected(true);
        }

        /** Rebuilds the item rows from the shared cart and refreshes all displayed totals. */
        void refresh() {
            itemsPanel.removeAll();
            List<OrderItem> items = cart.getItems();
            for (int i = 0; i < items.size(); i++)
                itemsPanel.add(createCartItemRow(i, items.get(i)));
            if (items.isEmpty()) {
                JLabel empty = label("Your cart is empty.", F_BODY, C_GREY);
                empty.setBorder(new EmptyBorder(16, 12, 16, 12));
                itemsPanel.add(empty);
            }
            cartTitle.setText("Your cart (" + cart.count() + (cart.count() == 1 ? " item)" : " items)"));
            double subtotal = cart.total();
            double deliveryFee = delivery.isSelected() ? Order.DELIVERY_FEE : 0;
            subtotalLbl.setText("Items subtotal: " + rs(subtotal));
            deliveryFeeLbl.setText("Delivery fee: " + rs(deliveryFee));
            totalLbl.setText("Total: " + rs(subtotal + deliveryFee));
            itemsPanel.revalidate(); itemsPanel.repaint();
        }

        private JPanel createCartItemRow(int index, OrderItem item) {
            RoundedPanel row = new RoundedPanel(new BorderLayout(8, 6), new Color(0xFAF6F6), 16);
            row.setBorder(new EmptyBorder(10, 12, 10, 12));

            JLabel nameLabel = label(item.getProduct().getName(), F_BOLD, C_DARK);
            row.add(nameLabel, BorderLayout.NORTH);

            JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)); controls.setOpaque(false);
            RoundButton minus = secondary("-"), plus = secondary("+"), remove = secondary("Remove");
            JLabel quantity = label("Qty: " + item.getQty(), F_BOLD, C_DARK);
            JLabel subtotal = label(rs(item.getSubtotal()), F_BOLD, C_ACCENT);
            minus.setToolTipText("Decrease quantity");
            plus.setToolTipText("Increase quantity");
            minus.addActionListener(e -> changeItemQuantity(index, -1));
            plus.addActionListener(e -> changeItemQuantity(index, 1));
            remove.addActionListener(e -> cart.remove(index));
            controls.add(minus); controls.add(quantity); controls.add(plus);
            controls.add(subtotal); controls.add(remove);
            row.add(controls, BorderLayout.SOUTH);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
            return row;
        }

        private void changeItemQuantity(int index, int delta) {
            if (index < 0 || index >= cart.getItems().size()) return;
            OrderItem item = cart.getItems().get(index);
            if (delta < 0 && item.getQty() <= 1) return;
            cart.change(index, delta);
        }

        private void placeOrder() {
            if (cart.isEmpty()) { warn(this, "Your cart is empty."); return; }
            String n = name.getText().trim(), ph = PHONE_PREFIX + normalizeLocalPhone(phone.getText()), addr = address.getText().trim();
            boolean del = delivery.isSelected();
            if (!isValidCustomerName(n)) { warn(this, "Invalid name. Use letters, spaces, apostrophes, or hyphens only."); return; }
            if (!isValidPakistaniPhone(ph)) { warn(this, "Invalid number."); return; }
            if (del && addr.isEmpty()) { warn(this, "Please enter a delivery address."); return; }
            if (!del && cod.isSelected()) {
                warn(this, "Cash on Delivery is only available for delivery orders.");
                return;
            }
            if (!cod.isSelected() && !online.isSelected()) {
                warn(this, "Select a payment method to place your order.");
                return;
            }
            PaymentMethod pm = online.isSelected() ? PaymentMethod.ONLINE : PaymentMethod.COD;
            if (pm == PaymentMethod.ONLINE) {
                int confirmation = JOptionPane.showConfirmDialog(this,
                        "Confirm online payment?",
                        "Confirm Payment", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (confirmation != JOptionPane.YES_OPTION) return;
            }
            Order o;
            try {
                o = Bakery.get().placeOrder(n, ph, del, addr, pm, cart.snapshot());
            } catch (IllegalStateException ex) {
                warn(this, ex.getMessage());
                return;
            }
            cart.clear(); name.setText(""); phone.setText(""); address.setText("");
            info(this, "Order placed successfully!\n\nYour order ID: " + o.getId() + "\nTotal: " + rs(o.getTotal())
                    + "\nPayment: " + (pm == PaymentMethod.ONLINE ? pm.toString() : pm + " (" + o.getPaymentStatus() + ")") + "\n\nKeep your order ID to track your order.");
            onPlaced.accept(o.getId());
        }
    }

    // =====================================================================
    //  ORDER TRACKING
    // =====================================================================
    static class TrackPanel extends JPanel {
        private static final int MAX_FAILS = 5, LOCK_SECONDS = 30, MAX_LOCK_SECONDS = 300;
        private final HintField idField = new HintField("e.g. MB-1001");
        private final JLabel msg = label(" ", F_BOLD, C_ALERT);
        private final FadeCard result = new FadeCard();
        private final JLabel orderIdLbl = label(" ", F_H2.deriveFont(26f), C_DARK);
        private final JLabel placedLbl = label(" ", F_BODY, C_GREY);
        private final JLabel statusMsg = label(" ", F_BOLD.deriveFont(15f), C_DARK);
        private final StatusBadge badge = new StatusBadge();
        private final TrackTimeline timeline = new TrackTimeline();
        // Only the ID of the order being shown is remembered; every live refresh looks the real order up again.
        private String verifiedId = null;
        private int failures = 0, lockouts = 0; private long lockedUntil = 0;

        TrackPanel() {
            setLayout(new BorderLayout()); setBackground(C_LIGHT); setBorder(new EmptyBorder(6, 20, 6, 20));
            // ---- verification card
            RoundedPanel form = new RoundedPanel(new BorderLayout(0, 14), Color.WHITE, 22); form.setBorder(new EmptyBorder(20, 24, 20, 24));
            JPanel head = new JPanel(new BorderLayout(14, 0)); head.setOpaque(false);
            head.add(new LockBadge(), BorderLayout.WEST);
            JPanel titles = new JPanel(); titles.setOpaque(false); titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
            JLabel t1 = label("Track your order", F_H2.deriveFont(22f), C_DARK), t2 = label("Enter your Order ID to see how your order is progressing.", F_BODY, C_GREY);
            t1.setAlignmentX(LEFT_ALIGNMENT); t2.setAlignmentX(LEFT_ALIGNMENT); titles.add(t1); titles.add(Box.createVerticalStrut(2)); titles.add(t2);
            head.add(titles, BorderLayout.CENTER);
            styleField(idField);
            idField.setPreferredSize(new Dimension(360, 44));
            RoundButton go = primary("Track order"); go.setPreferredSize(new Dimension(160, 44)); go.addActionListener(e -> track());
            idField.addActionListener(e -> track());
            JPanel fields = new JPanel(new GridBagLayout()); fields.setOpaque(false);
            GridBagConstraints gc = new GridBagConstraints(); gc.anchor = GridBagConstraints.WEST; gc.insets = new Insets(0, 0, 4, 12);
            gc.gridx = 0; gc.gridy = 0; fields.add(label("Order ID", F_SMALL.deriveFont(Font.BOLD), C_DARK), gc);
            gc.insets = new Insets(0, 0, 0, 12); gc.gridy = 1; gc.gridx = 0; fields.add(idField, gc);
            gc.gridx = 1; gc.insets = new Insets(0, 0, 0, 0); fields.add(go, gc);
            gc.gridx = 2; gc.weightx = 1; fields.add(Box.createHorizontalGlue(), gc);
            JPanel formBody = new JPanel(); formBody.setOpaque(false); formBody.setLayout(new BoxLayout(formBody, BoxLayout.Y_AXIS));
            fields.setAlignmentX(LEFT_ALIGNMENT); msg.setAlignmentX(LEFT_ALIGNMENT);
            formBody.add(fields); formBody.add(Box.createVerticalStrut(10)); formBody.add(msg);
            form.add(head, BorderLayout.NORTH); form.add(formBody, BorderLayout.CENTER);

            // ---- result card (status information only: no name, phone, address, payment, items or prices)
            result.setLayout(new BorderLayout(0, 12)); result.setBorder(new EmptyBorder(20, 24, 18, 24));
            JPanel top = new JPanel(new BorderLayout(12, 0)); top.setOpaque(false);
            JPanel idBox = new JPanel(); idBox.setOpaque(false); idBox.setLayout(new BoxLayout(idBox, BoxLayout.Y_AXIS));
            JLabel cap = label("ORDER ID", F_SMALL.deriveFont(Font.BOLD), C_GREY);
            cap.setAlignmentX(LEFT_ALIGNMENT); orderIdLbl.setAlignmentX(LEFT_ALIGNMENT); placedLbl.setAlignmentX(LEFT_ALIGNMENT);
            idBox.add(cap); idBox.add(orderIdLbl); idBox.add(Box.createVerticalStrut(2)); idBox.add(placedLbl);
            JPanel badgeWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4)); badgeWrap.setOpaque(false); badgeWrap.add(badge);
            top.add(idBox, BorderLayout.CENTER); top.add(badgeWrap, BorderLayout.EAST);
            JPanel mid = new JPanel(new BorderLayout(0, 6)); mid.setOpaque(false);
            JSeparator sep = new JSeparator(); sep.setForeground(new Color(0xE3D8D8));
            JPanel sepWrap = new JPanel(new BorderLayout()); sepWrap.setOpaque(false); sepWrap.setBorder(new EmptyBorder(0, 0, 4, 0)); sepWrap.add(sep);
            mid.add(sepWrap, BorderLayout.NORTH); mid.add(timeline, BorderLayout.CENTER);
            statusMsg.setHorizontalAlignment(SwingConstants.CENTER);
            JPanel bottom = new JPanel(new BorderLayout(0, 8)); bottom.setOpaque(false);
            bottom.add(statusMsg, BorderLayout.NORTH);
            result.add(top, BorderLayout.NORTH); result.add(mid, BorderLayout.CENTER); result.add(bottom, BorderLayout.SOUTH);
            result.setVisible(false);

            JPanel col = new JPanel(); col.setOpaque(false); col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
            form.setAlignmentX(LEFT_ALIGNMENT); result.setAlignmentX(LEFT_ALIGNMENT);
            col.add(form); col.add(Box.createVerticalStrut(16)); col.add(result);
            JPanel wrap = new JPanel(null); wrap.setOpaque(false); wrap.add(col);
            wrap.setLayout(new LayoutManager() {                              // keeps the content in a centred column, at most 800 px wide
                public void addLayoutComponent(String n, Component c) { }
                public void removeLayoutComponent(Component c) { }
                public Dimension preferredLayoutSize(Container p) { return new Dimension(640, col.getPreferredSize().height); }
                public Dimension minimumLayoutSize(Container p) { return new Dimension(560, col.getPreferredSize().height); }
                public void layoutContainer(Container p) {
                    int w = Math.min(800, p.getWidth()), x = (p.getWidth() - w) / 2;
                    col.setBounds(x, 6, w, Math.min(p.getHeight(), col.getPreferredSize().height));
                }
            });
            JScrollPane sp = new JScrollPane(wrap); sp.setBorder(null); sp.setOpaque(false); sp.getViewport().setOpaque(false);
            sp.getVerticalScrollBar().setUnitIncrement(16);
            add(sp, BorderLayout.CENTER);

            Bakery.get().addListener(() -> {                                  // live updates when the administrator changes a status
                if (SwingUtilities.isEventDispatchThread()) refreshLive(); else SwingUtilities.invokeLater(this::refreshLive);
            });
            addHierarchyListener(e -> {                                       // leaving the page wipes the order shown and the typed ID
                if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && !isShowing()) resetAll();
            });
        }

        /** Called after checkout: the customer has just received this Order ID, so the order is shown straight away. */
        void showOrder(String id) {
            resetAll(); idField.setText(id);
            Order o = Bakery.get().find(id);                                  // the system itself just issued this ID, so it skips the guess counter
            if (o == null) return;
            verifiedId = o.getId(); render(o); result.setVisible(true); result.fadeIn();
        }

        private void showError(String text) { msg.setText(text); }

        private void track() {
            clearResult(); showError(" ");
            long now = System.currentTimeMillis();
            if (now < lockedUntil) {
                showError("Too many unsuccessful attempts. Please wait " + ((lockedUntil - now + 999) / 1000) + " seconds and try again.");
                return;
            }
            String id = idField.getText().trim().toUpperCase();
            if (id.isEmpty()) { showError("Please enter your Order ID."); return; }
            if (!id.matches("MB-[A-Z]?[0-9]{1,9}(-[A-Z0-9]{4,8})?")) { showError("That is not a valid Order ID. Order IDs look like MB-1001."); return; }
            Order o = Bakery.get().find(id);
            if (o == null) {
                failures++;
                if (failures >= MAX_FAILS) {                                     // repeated guessing: lock the form, and lock for longer each time
                    failures = 0; int secs = Math.min(MAX_LOCK_SECONDS, LOCK_SECONDS << Math.min(lockouts, 4)); lockouts++;
                    lockedUntil = now + secs * 1000L;
                    showError("Too many unsuccessful attempts. Please wait " + secs + " seconds and try again.");
                } else showError("No order was found for that Order ID. Please check it and try again.");
                return;
            }
            failures = 0; lockouts = 0; verifiedId = o.getId();
            render(o); result.setVisible(true); result.fadeIn(); revalidate(); repaint();
        }

        /** Re-reads the real order from the bakery (the same object the Admin Portal updates) every time something changes. */
        private void refreshLive() {
            if (verifiedId == null) return;
            Order o = Bakery.get().find(verifiedId);
            if (o == null) { clearResult(); showError("This order is no longer available."); return; }
            render(o);
        }
        private void render(Order o) {
            boolean delivery = o.isDelivery(); OrderStatus st = o.getStatus();
            orderIdLbl.setText(o.getId());
            placedLbl.setText("Placed on " + new SimpleDateFormat("dd MMM yyyy, hh:mm a").format(o.getPlacedAt()));
            badge.setStatus(st, delivery); timeline.setStatus(st.ordinal(), delivery);
            statusMsg.setText(describe(st, delivery));
        }
        private static String describe(OrderStatus s, boolean delivery) {
            switch (s) {
                case PLACED: return "We have received your order and will confirm it shortly.";
                case CONFIRMED: return "Your order is confirmed. The bakers will start on it soon.";
                case PREPARING: return "Your order is being freshly prepared.";
                case READY: return delivery ? "Your order is out for delivery." : "Your order is ready. Please collect it from the bakery.";
                default: return "Your order is complete. Thank you for choosing Crumb & Whisk!";
            }
        }
        private void clearResult() { verifiedId = null; result.setVisible(false); timeline.setStatus(-1, false); }
        private void resetAll() { clearResult(); showError(" "); idField.setText(""); }
    }

    /** Text field that shows a grey hint while it is empty. */
    static class HintField extends JTextField {
        private final String hint;
        HintField(String hint) { this.hint = hint; }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(new Color(0xB9A0A0)); g2.setFont(getFont());
                Insets in = getInsets(); g2.drawString(hint, in.left, (getHeight() + g2.getFontMetrics().getAscent() - g2.getFontMetrics().getDescent()) / 2);
                g2.dispose();
            }
        }
    }

    /** Rounded white card that fades in when it appears. */
    static class FadeCard extends RoundedPanel {
        private float alpha = 1f; private final javax.swing.Timer t;
        FadeCard() {
            super(null, Color.WHITE, 22);
            t = new javax.swing.Timer(16, e -> { alpha = Math.min(1f, alpha + 0.08f); if (alpha >= 1f) ((javax.swing.Timer) e.getSource()).stop(); repaint(); });
        }
        void fadeIn() { alpha = 0f; t.restart(); }
        @Override public void paint(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha))));
            super.paint(g2); g2.dispose();
        }
    }

    /** Round padlock badge for the verification card. */
    static class LockBadge extends JComponent {
        LockBadge() { setPreferredSize(new Dimension(52, 52)); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, C_ACCENT, 0, 52, C_DARK)); g.fillOval(0, 0, 50, 50);
            g.setColor(Color.WHITE); g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawArc(17, 11, 16, 18, 0, 180); g.drawLine(17, 20, 17, 23); g.drawLine(33, 20, 33, 23);
            g.fillRoundRect(14, 23, 22, 16, 6, 6);
            g.setColor(C_ACCENT); g.fillOval(23, 28, 5, 5); g.fillRect(24, 31, 3, 5);
            g.dispose();
        }
    }

    /** Pill-shaped status badge: dot + status text, coloured by stage. */
    static class StatusBadge extends JComponent {
        private OrderStatus status = OrderStatus.PLACED; private boolean delivery;
        StatusBadge() { setPreferredSize(new Dimension(190, 36)); setFont(F_BOLD); }
        void setStatus(OrderStatus s, boolean delivery) { status = s; this.delivery = delivery; repaint(); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean strong = status.ordinal() >= OrderStatus.PREPARING.ordinal();
            Color fill = status == OrderStatus.PLACED ? C_TINT : status == OrderStatus.CONFIRMED ? C_SOFT : status == OrderStatus.PREPARING ? C_ACCENT : C_DARK;
            Color fg = status == OrderStatus.PLACED || status == OrderStatus.CONFIRMED ? C_DARK : Color.WHITE;
            g.setColor(fill); g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
            if (!strong) { g.setColor(C_SOFT); g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight()); }
            g.setColor(fg); g.fillOval(14, getHeight() / 2 - 4, 8, 8);
            g.setFont(F_BOLD); FontMetrics fm = g.getFontMetrics(); String t = status.label(delivery);
            g.drawString(t, 30, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g.dispose();
        }
    }

    /** Animated five-step tracker: the filled rail glides to the current step and the current step softly pulses. */
    static class TrackTimeline extends JComponent {
        private int cur = -1; private boolean delivery; private float shown = 0f, phase = 0f;
        private final javax.swing.Timer anim = new javax.swing.Timer(30, e -> {
            float target = Math.max(0, cur);
            if (Math.abs(target - shown) > 0.01f) shown += (target - shown) * 0.18f; else shown = target;
            phase += 0.12f; repaint();
        });
        TrackTimeline() { setPreferredSize(new Dimension(640, 128)); setMinimumSize(new Dimension(420, 128)); }
        void setStatus(int ordinal, boolean delivery) { cur = ordinal; this.delivery = delivery; if (ordinal < 0) shown = 0; repaint(); }
        @Override public void addNotify() { super.addNotify(); anim.start(); }
        @Override public void removeNotify() { anim.stop(); super.removeNotify(); }
        private static String[] wrap(String s, FontMetrics fm, int w) {
            if (fm.stringWidth(s) <= w || !s.contains(" ")) return new String[]{s};
            int cut = s.lastIndexOf(' '); return new String[]{s.substring(0, cut), s.substring(cut + 1)};
        }
        @Override protected void paintComponent(Graphics g0) {
            if (cur < 0) return;
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            OrderStatus[] st = OrderStatus.values(); int n = st.length, d = 46, y = 36;
            int margin = Math.max(d / 2 + 8, getWidth() / 10); int step = (getWidth() - 2 * margin) / (n - 1);
            Color track = new Color(0xE9DEDE);
            g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(track); g.drawLine(margin, y, margin + (n - 1) * step, y);
            int fillX = margin + Math.round(shown * step);
            if (fillX > margin) { g.setPaint(new GradientPaint(margin, 0, C_SOFT, fillX, 0, C_ACCENT)); g.drawLine(margin, y, fillX, y); }
            for (int i = 0; i < n; i++) {
                int x = margin + i * step; boolean done = i < cur, now = i == cur;
                if (now && cur < n - 1) {                                        // soft pulse around the active step
                    float p = (float) (0.5 + 0.5 * Math.sin(phase)); int r = d / 2 + 4 + Math.round(p * 5);
                    g.setColor(new Color(0x79, 0x47, 0x47, Math.round(70 * (1 - p)) + 20)); g.fillOval(x - r, y - r, 2 * r, 2 * r);
                }
                if (done || now) { g.setPaint(new GradientPaint(x, y - d / 2, now ? C_ACCENT : blend(C_ACCENT, C_SOFT, 0.15f), x, y + d / 2, C_DARK)); g.fillOval(x - d / 2, y - d / 2, d, d); }
                else { g.setColor(Color.WHITE); g.fillOval(x - d / 2, y - d / 2, d, d); g.setColor(C_SOFT); g.setStroke(new BasicStroke(2f)); g.drawOval(x - d / 2, y - d / 2, d, d); }
                Color ic = done || now ? Color.WHITE : C_SOFT;
                if (done) stepGlyph(g, -1, false, x, y, ic); else stepGlyph(g, i, delivery, x, y, ic);       // finished steps show a tick
                g.setFont(now ? F_BOLD.deriveFont(12.5f) : F_SMALL); FontMetrics fm = g.getFontMetrics();
                g.setColor(now ? C_DARK : done ? C_TEXT : C_GREY);
                String[] lines = wrap(st[i].label(delivery), fm, step - 8);
                for (int k = 0; k < lines.length; k++) g.drawString(lines[k], x - fm.stringWidth(lines[k]) / 2, y + d / 2 + 18 + k * (fm.getHeight() - 1));
            }
            g.dispose();
        }
        /** Small vector icons: -1 tick, 0 receipt, 1 confirmed, 2 mixing bowl, 3 bag / delivery van, 4 flag. */
        static void stepGlyph(Graphics2D g0, int kind, boolean delivery, int cx, int cy, Color c) {
            Graphics2D g = (Graphics2D) g0.create(); g.translate(cx, cy); g.setColor(c);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (kind) {
                case -1: g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.drawPolyline(new int[]{-8, -2, 8}, new int[]{0, 6, -6}, 3); break;
                case 0: g.drawRoundRect(-7, -9, 14, 18, 4, 4); g.drawLine(-3, -3, 3, -3); g.drawLine(-3, 1, 3, 1); g.drawLine(-3, 5, 1, 5); break;
                case 1: g.drawOval(-9, -9, 18, 18); g.drawPolyline(new int[]{-4, -1, 5}, new int[]{0, 3, -3}, 3); break;
                case 2: g.draw(new java.awt.geom.Arc2D.Double(-10, -6, 20, 18, 180, 180, java.awt.geom.Arc2D.OPEN)); g.drawLine(-10, 3, 10, 3);
                    g.drawLine(2, -3, 8, -10); g.drawLine(-4, -5, -4, -9); break;
                case 3:
                    if (delivery) { g.drawRect(-11, -7, 13, 11); g.drawPolyline(new int[]{2, 7, 11, 11, 2}, new int[]{-3, -3, 1, 4, 4}, 5); g.drawOval(-8, 4, 5, 5); g.drawOval(4, 4, 5, 5); }
                    else { g.drawRoundRect(-8, -4, 16, 13, 4, 4); g.draw(new java.awt.geom.Arc2D.Double(-4, -10, 8, 12, 0, 180, java.awt.geom.Arc2D.OPEN)); }
                    break;
                default: g.drawLine(-6, -9, -6, 9); g.drawPolyline(new int[]{-6, 8, -6}, new int[]{-9, -4, 1}, 3);
            }
            g.dispose();
        }
    }

    // =====================================================================
    //  ADMIN DASHBOARD - VISUAL COMPONENTS (drawing only, no business logic)
    // =====================================================================
    /** Small vector glyphs for the dashboard (no image files needed). */
    static class AdminArt {
        static final int ORDERS = 0, REVENUE = 1, PENDING = 2, STOCK = 3, PROFIT = 4, POPULAR = 5, HOME = 6;
        static void glyph(Graphics2D g0, int kind, int cx, int cy, int r, Color c) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setColor(c); g.setStroke(new BasicStroke(Math.max(1.6f, r / 6f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.translate(cx, cy);
            switch (kind) {
                case ORDERS:                                                     // clipboard with lines
                    g.drawRoundRect(-r * 3 / 4, -r, r * 3 / 2, r * 2, 5, 5);
                    g.drawLine(-r / 3, -r / 4, r / 3, -r / 4); g.drawLine(-r / 3, r / 6, r / 3, r / 6); g.drawLine(-r / 3, r * 9 / 16, r / 8, r * 9 / 16);
                    g.fillRoundRect(-r / 3, -r - 2, r * 2 / 3, 5, 3, 3); break;
                case REVENUE: {                                                  // coin with "Rs"
                    g.drawOval(-r, -r, r * 2, r * 2);
                    g.setFont(F_BOLD.deriveFont(Font.BOLD, r * 0.95f)); FontMetrics fm = g.getFontMetrics();
                    g.drawString("Rs", -fm.stringWidth("Rs") / 2, (fm.getAscent() - fm.getDescent()) / 2); break; }
                case PENDING:                                                    // clock
                    g.drawOval(-r, -r, r * 2, r * 2); g.drawLine(0, 0, 0, -r * 2 / 3); g.drawLine(0, 0, r / 2, r / 4); break;
                case STOCK: {                                                    // warning triangle
                    Path2D.Double t = new Path2D.Double(); t.moveTo(0, -r); t.lineTo(r, r * 3 / 4); t.lineTo(-r, r * 3 / 4); t.closePath(); g.draw(t);
                    g.drawLine(0, -r / 4, 0, r / 4); g.fillOval(-1, r / 2 - 2, 3, 3); break; }
                case PROFIT:                                                     // rising line
                    g.drawLine(-r, r * 2 / 3, -r / 3, 0); g.drawLine(-r / 3, 0, r / 5, r / 3); g.drawLine(r / 5, r / 3, r, -r * 2 / 3);
                    g.drawLine(r, -r * 2 / 3, r - r / 2, -r * 2 / 3); g.drawLine(r, -r * 2 / 3, r, -r * 2 / 3 + r / 2); break;
                case HOME:                                                       // four tiles
                    g.drawRoundRect(-r, -r, r * 9 / 10, r * 9 / 10, 3, 3); g.drawRoundRect(r / 10, -r, r * 9 / 10, r * 9 / 10, 3, 3);
                    g.drawRoundRect(-r, r / 10, r * 9 / 10, r * 9 / 10, 3, 3); g.drawRoundRect(r / 10, r / 10, r * 9 / 10, r * 9 / 10, 3, 3); break;
                default:                                                         // bars
                    g.fillRoundRect(-r, 0, r * 2 / 3, r, 2, 2); g.fillRoundRect(-r / 3, -r / 2, r * 2 / 3, r * 3 / 2, 2, 2); g.fillRoundRect(r / 3, -r, r * 2 / 3, r * 2, 2, 2);
            }
            g.dispose();
        }
    }

    /** Eye icon: open when the value is shown, struck through when it is hidden. */
    static void eyeIcon(Graphics2D g0, int cx, int cy, boolean open, Color c) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(c); g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.translate(cx, cy);
        Path2D.Double eye = new Path2D.Double(); eye.moveTo(-9, 0); eye.curveTo(-5, -7, 5, -7, 9, 0); eye.curveTo(5, 7, -5, 7, -9, 0); g.draw(eye);
        g.fillOval(-3, -3, 6, 6);
        if (!open) { g.setColor(Color.WHITE); g.setStroke(new BasicStroke(4.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.drawLine(-8, 8, 8, -8);
            g.setColor(c); g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.drawLine(-8, 8, 8, -8); }
        g.dispose();
    }

    /** Dashboard figure card: icon, caption, large value and a one-line note. Hover lifts it slightly. */
    static class StatCard extends JComponent {
        private final String caption; private final int kind;
        private String value = "0", note = " "; private Color tone;
        private boolean maskable = false, masked = true; private boolean overEye = false;     // revenue card: value hidden until the eye is clicked
        private float hover = 0f; private boolean over = false;
        private final javax.swing.Timer fade = new javax.swing.Timer(16, e -> {
            float t = over ? 1f : 0f; hover += (t - hover) * 0.3f;
            if (Math.abs(t - hover) < 0.02f) { hover = t; ((javax.swing.Timer) e.getSource()).stop(); }
            repaint();
        });
        StatCard(String caption, int kind, Color tone) {
            this.caption = caption; this.kind = kind; this.tone = tone;
            setPreferredSize(new Dimension(190, 100)); setMinimumSize(new Dimension(150, 96));
            ToolTipManager.sharedInstance().registerComponent(this);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { over = true; fade.start(); }
                @Override public void mouseExited(MouseEvent e) { over = false; overEye = false; setCursor(Cursor.getDefaultCursor()); fade.start(); repaint(); }
                @Override public void mouseClicked(MouseEvent e) { if (maskable && eyeBounds().contains(e.getPoint())) { masked = !masked; repaint(); } }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    boolean on = maskable && eyeBounds().contains(e.getPoint());
                    if (on != overEye) { overEye = on; setCursor(Cursor.getPredefinedCursor(on ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR)); repaint(); }
                }
            });
        }
        /** Makes this card's amount hidden by default, with an eye button to show or hide it. */
        StatCard maskable() { maskable = true; return this; }
        private Rectangle eyeBounds() { return new Rectangle(getWidth() - 7 - 34, 6, 32, 28); }
        @Override public String getToolTipText(MouseEvent e) {
            if (maskable && eyeBounds().contains(e.getPoint())) return masked ? "Show revenue" : "Hide revenue";
            return null;
        }
        void set(String value, String note, Color tone) { this.value = value; this.note = note; this.tone = tone; repaint(); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight(), lift = Math.round(2 * hover), x = 3, y = 2 - lift, cw = w - 7, ch = h - 8;
            for (int i = 5; i >= 1; i--) { g.setColor(new Color(0x4E, 0x20, 0x20, Math.round((3 + hover * 4) * (6 - i)))); g.fillRoundRect(x - i + 1, y + 2 + lift - i / 2 + 1, cw + 2 * i - 2, ch + i, 22 + i, 22 + i); }
            g.setColor(Color.WHITE); g.fillRoundRect(x, y, cw, ch, 20, 20);
            g.setColor(blend(C_SOFT, tone, hover * 0.6f)); g.drawRoundRect(x, y, cw - 1, ch - 1, 20, 20);
            Shape old = g.getClip(); g.clip(new java.awt.geom.RoundRectangle2D.Double(x, y, cw, ch, 20, 20));
            g.setColor(tone); g.fillRect(x, y, 5, ch); g.setClip(old);                                       // coloured edge
            int d = 46, ix = x + 20, iy = y + (ch - d) / 2;
            g.setColor(blend(tone, Color.WHITE, 0.86f)); g.fillOval(ix, iy, d, d);
            AdminArt.glyph(g, kind, ix + d / 2, iy + d / 2, 10, tone);
            int tx = ix + d + 14, tw = cw - (tx - x) - 10;
            g.setFont(F_SMALL); g.setColor(C_GREY); g.drawString(ModernUi.fit(caption, g.getFontMetrics(), tw), tx, y + 28);
            g.setFont(F_H2.deriveFont(Font.BOLD, 25f)); g.setColor(C_DARK);
            String shownValue = maskable && masked ? "Rs. \u2022\u2022\u2022\u2022\u2022\u2022" : value;
            g.drawString(ModernUi.fit(shownValue, g.getFontMetrics(), tw), tx, y + 58);
            if (maskable) {
                Rectangle eb = eyeBounds();
                if (overEye) { g.setColor(new Color(0xF3ECEC)); g.fillRoundRect(eb.x, eb.y, eb.width, eb.height, 12, 12); }
                eyeIcon(g, eb.x + eb.width / 2, eb.y + eb.height / 2, !masked, overEye ? C_DARK : C_GREY);
            }
            g.setFont(F_SMALL); g.setColor(tone == C_ALERT ? C_ALERT : C_GREY); g.drawString(ModernUi.fit(note, g.getFontMetrics(), tw), tx, y + 77);
            g.dispose();
        }
    }

    /** Sidebar entry: glyph + text + optional count badge. Hover fades in, the selected entry is filled. */
    static class NavItem extends JComponent {
        private final String text; private final int kind; private final Runnable onClick;
        private boolean sel = false, compact = false; private String badge = null; private Color badgeColor = C_ACCENT;
        private float hover = 0f; private boolean over = false;
        private final javax.swing.Timer fade = new javax.swing.Timer(16, e -> {
            float t = over ? 1f : 0f; hover += (t - hover) * 0.3f;
            if (Math.abs(t - hover) < 0.02f) { hover = t; ((javax.swing.Timer) e.getSource()).stop(); }
            repaint();
        });
        void setCompact(boolean c) { compact = c; setToolTipText(c ? text : null); repaint(); }
        NavItem(String text, int kind, Runnable onClick) {
            this.text = text; this.kind = kind; this.onClick = onClick;
            setPreferredSize(new Dimension(190, 46)); setMaximumSize(new Dimension(Integer.MAX_VALUE, 46)); setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { over = true; fade.start(); }
                @Override public void mouseExited(MouseEvent e) { over = false; fade.start(); }
                @Override public void mouseClicked(MouseEvent e) { requestFocusInWindow(); onClick.run(); }
            });
            addFocusListener(new FocusAdapter() { @Override public void focusGained(FocusEvent e) { repaint(); } @Override public void focusLost(FocusEvent e) { repaint(); } });
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "go");
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "go");
            getActionMap().put("go", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { onClick.run(); } });
        }
        void setSelectedItem(boolean b) { sel = b; repaint(); }
        void setBadge(String t, Color c) { badge = t; badgeColor = c; repaint(); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            if (sel) { g.setPaint(new GradientPaint(0, 0, C_ACCENT, w, 0, blend(C_ACCENT, C_DARK, 0.25f))); g.fillRoundRect(2, 3, w - 4, h - 6, 16, 16); }
            else if (hover > 0) { g.setColor(new Color(255, 255, 255, Math.round(34 * hover))); g.fillRoundRect(2, 3, w - 4, h - 6, 16, 16); }
            Color fg = sel ? Color.WHITE : blend(new Color(0xE6D6D6), Color.WHITE, hover);
            AdminArt.glyph(g, kind, compact ? w / 2 : 28, h / 2, 9, fg);
            g.setFont(F_BOLD.deriveFont(12.5f)); g.setColor(fg);
            FontMetrics fm = g.getFontMetrics(); int bw = badge == null ? 0 : 28;
            if (!compact) g.drawString(ModernUi.fit(text, fm, w - 48 - bw - 8), 46, (h + fm.getAscent() - fm.getDescent()) / 2);
            if (badge != null && compact) {
                g.setFont(F_SMALL.deriveFont(Font.BOLD, 10f)); FontMetrics bm = g.getFontMetrics(); int bwid = Math.max(16, bm.stringWidth(badge) + 8);
                g.setColor(badgeColor); g.fillRoundRect(w - bwid - 4, 5, bwid, 16, 16, 16); g.setColor(Color.WHITE); g.drawString(badge, w - bwid - 4 + (bwid - bm.stringWidth(badge)) / 2, 17);
            } else if (badge != null) {
                g.setFont(F_SMALL.deriveFont(Font.BOLD, 11f)); FontMetrics bm = g.getFontMetrics();
                int bwid = Math.max(22, bm.stringWidth(badge) + 14), bx = w - bwid - 12, by = (h - 20) / 2;
                g.setColor(badgeColor); g.fillRoundRect(bx, by, bwid, 20, 20, 20);
                g.setColor(Color.WHITE); g.drawString(badge, bx + (bwid - bm.stringWidth(badge)) / 2, by + 14);
            }
            if (isFocusOwner()) { g.setColor(new Color(255, 255, 255, 140)); g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{3f, 3f}, 0f)); g.drawRoundRect(4, 5, w - 9, h - 11, 12, 12); }
            g.dispose();
        }
    }

    /** Rounded dark sidebar background. */
    static class SidebarPanel extends JPanel {
        SidebarPanel(LayoutManager lm) { super(lm); setOpaque(false); }
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (int i = 4; i >= 1; i--) { g.setColor(new Color(0x4E, 0x20, 0x20, 8 + (4 - i) * 3)); g.fillRoundRect(2 - i + 1, 3 - i / 2 + 1, getWidth() - 4 + 2 * i - 2, getHeight() - 6 + i, 26 + i, 26 + i); }
            g.setPaint(new GradientPaint(0, 0, blend(C_DARK, C_ACCENT, 0.12f), 0, getHeight(), blend(C_DARK, Color.BLACK, 0.35f)));
            g.fillRoundRect(2, 1, getWidth() - 5, getHeight() - 6, 26, 26);
            g.dispose(); super.paintComponent(g0);
        }
    }

    /** Shared helpers for the modern admin tables. */
    static class ModernUi {
        static String fit(String s, FontMetrics fm, int w) {
            if (s == null) return "";
            if (fm.stringWidth(s) <= w) return s;
            while (s.length() > 1 && fm.stringWidth(s + "…") > w) s = s.substring(0, s.length() - 1);
            return s + "…";
        }
        static Color rowColor(JTable t, int row, boolean sel) {
            if (sel) return C_TINT;
            Object hr = t.getClientProperty("hoverRow");
            if (hr instanceof Integer && (Integer) hr == row) return C_HOVER;
            return row % 2 == 0 ? Color.WHITE : new Color(0xFCFAFA);
        }
        /** {background, text} colours of a status pill for the given label. */
        static Color[] badge(String text) {
            switch (text == null ? "" : text) {
                case "Order Placed":     return new Color[]{new Color(0xF8E9C4), new Color(0x7A5200)};
                case "Order Confirmed":  return new Color[]{new Color(0xEBD6D6), new Color(0x794747)};
                case "Preparing":        return new Color[]{new Color(0xF2DEC8), new Color(0x7A4A12)};
                case "Ready for Pickup": case "Out for Delivery": return new Color[]{new Color(0xD6E8E3), new Color(0x1E5B52)};
                case "Completed": case "Paid": case "OK": return new Color[]{new Color(0xD4E8D4), new Color(0x24592A)};
                case "Pending":          return new Color[]{new Color(0xF8E9C4), new Color(0x7A5200)};
                case "LOW STOCK":        return new Color[]{new Color(0xF6D8D5), C_ALERT};
                case "Delivery":         return new Color[]{new Color(0xEBD6D6), new Color(0x4E2020)};
                default:                 return new Color[]{new Color(0xEEE8E8), new Color(0x4E2020)};     // Pickup and anything else
            }
        }
        /** Plain text cell: padded, zebra rows, hover + selection tint, hairline separators. */
        static class TextCell extends DefaultTableCellRenderer {
            private final boolean bold; private final int align;
            TextCell(boolean bold, int align) { this.bold = bold; this.align = align; }
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, v, false, false, r, c);
                setBackground(rowColor(t, r, sel)); setForeground(sel ? C_DARK : C_TEXT); setFont(bold || sel ? F_BOLD : F_BODY);
                setHorizontalAlignment(align); setOpaque(true);
                setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xEFE6E6)), new EmptyBorder(0, 12, 0, 12)));
                setToolTipText(v == null ? null : v.toString());
                return this;
            }
        }
        /** Rounded status pill cell. */
        static class BadgeCell extends DefaultTableCellRenderer {
            private String txt = ""; private Color pillBg = Color.LIGHT_GRAY, pillFg = Color.BLACK;
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                txt = v == null ? "" : v.toString(); Color[] cs = badge(txt); pillBg = cs[0]; pillFg = cs[1];
                setText(""); setOpaque(true); setBackground(rowColor(t, r, sel));
                setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xEFE6E6)));
                return this;
            }
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setColor(getBackground()); g.fillRect(0, 0, getWidth(), getHeight());
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setFont(F_SMALL.deriveFont(Font.BOLD, 12f)); FontMetrics fm = g.getFontMetrics();
                String t = fit(txt, fm, getWidth() - 36); int pw = fm.stringWidth(t) + 22, ph = 24, x = 12, y = (getHeight() - 1 - ph) / 2;
                g.setColor(pillBg); g.fillRoundRect(x, y, pw, ph, ph, ph);
                g.setColor(pillFg); g.drawString(t, x + 11, y + (ph + fm.getAscent() - fm.getDescent()) / 2);
                g.dispose();
            }
        }
        /** Light header, bold wine text, strong bottom rule. */
        static void header(JTable t) {
            DefaultTableCellRenderer hr = new DefaultTableCellRenderer() {
                @Override public Component getTableCellRendererComponent(JTable tb, Object v, boolean sel, boolean foc, int r, int c) {
                    super.getTableCellRendererComponent(tb, v, false, false, r, c);
                    setOpaque(true); setBackground(new Color(0xF3ECEC)); setForeground(C_DARK); setFont(F_BOLD.deriveFont(12.5f));
                    setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, C_SOFT), new EmptyBorder(0, 14, 0, 14)));
                    int[] rc = (int[]) tb.getClientProperty("rightCols"); boolean right = false;
                    if (rc != null) for (int x : rc) if (x == tb.convertColumnIndexToModel(c)) right = true;
                    setHorizontalAlignment(right ? SwingConstants.RIGHT : SwingConstants.LEFT);
                    return this;
                }
            };
            t.getTableHeader().setDefaultRenderer(hr); t.getTableHeader().setReorderingAllowed(false);
            t.getTableHeader().setPreferredSize(new Dimension(100, 40));
        }
        /** Applies the modern look. badgeCols use status pills; rightCols are right-aligned numbers; boldCol is emphasised. */
        static void style(JTable t, int boldCol, int[] badgeCols, int[] rightCols) {
            t.setRowHeight(46); t.setShowGrid(false); t.setIntercellSpacing(new Dimension(0, 0));
            t.setFont(F_BODY); t.setForeground(C_TEXT); t.setBackground(Color.WHITE); t.setFillsViewportHeight(true);
            t.setSelectionBackground(C_TINT); t.setSelectionForeground(C_DARK); t.setFocusable(true);
            t.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS); t.putClientProperty("rightCols", rightCols);
            for (int c = 0; c < t.getColumnCount(); c++) {
                boolean badge = false, right = false;
                for (int b : badgeCols) if (b == c) badge = true;
                for (int rc : rightCols) if (rc == c) right = true;
                t.getColumnModel().getColumn(c).setCellRenderer(badge ? new BadgeCell() : new TextCell(c == boldCol, right ? SwingConstants.RIGHT : SwingConstants.LEFT));
            }
            header(t);
            MouseAdapter hover = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) { int r = t.rowAtPoint(e.getPoint()); Object o = t.getClientProperty("hoverRow"); if (o == null || (Integer) o != r) { t.putClientProperty("hoverRow", r); t.repaint(); } }
                @Override public void mouseExited(MouseEvent e) { t.putClientProperty("hoverRow", -1); t.repaint(); }
            };
            t.addMouseMotionListener(hover); t.addMouseListener(hover);
        }
        /** Scroll pane without its own frame, so it sits cleanly inside a card. */
        static JScrollPane scroller(Component c) {
            JScrollPane sp = new JScrollPane(c); sp.setBorder(BorderFactory.createLineBorder(new Color(0xEADFDF)));
            sp.getViewport().setBackground(Color.WHITE); sp.getVerticalScrollBar().setUnitIncrement(18);
            return sp;
        }
        static JLabel title(String t) { return label(t, F_H2.deriveFont(18f), C_DARK); }
        /** Small rounded status pill used in the order details header. */
        static class StatusPill extends JComponent {
            private final String text;
            StatusPill(String text) {
                this.text = text; setFont(F_SMALL.deriveFont(Font.BOLD, 12f));
                setPreferredSize(new Dimension(getFontMetrics(getFont()).stringWidth(text) + 26, 26));
            }
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                Color[] cs = badge(text); FontMetrics fm = g.getFontMetrics(getFont()); g.setFont(getFont());
                g.setColor(cs[0]); g.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g.setColor(cs[1]); g.drawString(text, (getWidth() - fm.stringWidth(text)) / 2, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g.dispose();
            }
        }
    }

    // =====================================================================
    //  ADMIN PORTAL  (orders, low stock, profit, popularity)
    // =====================================================================
    static class AdminPortal extends JPanel {
        private final Bakery bakery = Bakery.get();
        private final RoundedPanel alertBox = new RoundedPanel(new BorderLayout(), C_SOFT, 16);
        private final JLabel alertLabel = label(" ", F_BOLD, C_DARK);
        // orders
        private final DefaultTableModel orderModel = readOnlyModel("Order ID", "Customer", "Type", "Items", "Total", "Payment", "Pay status", "Status");
        private final JTable orderTable = new JTable(orderModel);
        private final JPanel orderDetails = new JPanel();          // styled, read-only details of the selected order
        private JPanel orderDetailCard;
        private boolean refreshingOrders = false;
        private List<Order> shown = new ArrayList<>();
        // inventory
        private final DefaultTableModel stockModel = readOnlyModel("Ingredient", "In stock", "Minimum", "Status");
        private final JTable stockTable = new JTable(stockModel);
        // profit
        private final DefaultTableModel profitModel = readOnlyModel("Product", "Units sold", "Est. sales", "Est. cost", "Est. gross profit", "Est. margin");
        private final JTextField overhead = new JTextField("0", 8);
        private final JLabel ordersValue = label("0", F_H2, C_ACCENT);
        private final JLabel revenueValue = label(rs(0), F_H2, C_ACCENT);
        private final JLabel marginValue = label("0.0%", F_H2, C_ACCENT);
        private final JLabel marginCaption = label("Profit Margin", F_SMALL, C_GREY);
        private final RoundedPanel profitNote = new RoundedPanel(new BorderLayout(), C_TINT, 16);
        private final JLabel profitNoteLabel = label(" ", F_BOLD, C_DARK);
        private static final double MAX_EXPENSE = 1_000_000_000.0;      // largest expense amount accepted by the estimator
        // popularity
        private final BarChart chart = new BarChart();
        private final JLabel popSummary = label(" ", F_BOLD, C_DARK);
        // dashboard shell: sidebar navigation + figure cards + one page per section
        private final CardLayout pages = new CardLayout();
        private final JPanel pageHost = new JPanel(pages);
        private final NavItem[] nav = new NavItem[5];
        private final JTable homeTable = new JTable(orderModel);       // read-only list on the dashboard (same data as the Orders page)
        private JPanel cardRow;
        private final StatCard cardOrders = new StatCard("Total orders", AdminArt.ORDERS, C_ACCENT);
        private final StatCard cardRevenue = new StatCard("Revenue (all orders)", AdminArt.REVENUE, new Color(0x2F6B3A)).maskable();
        private final StatCard cardPending = new StatCard("Pending orders", AdminArt.PENDING, new Color(0xB07A12));
        private final StatCard cardLow = new StatCard("Low-stock alerts", AdminArt.STOCK, new Color(0x2F6B3A));
        void showInventoryTab() { selectPage(2); }      // "Inventory & Low Stock" page
        private void selectPage(int i) {
            pages.show(pageHost, String.valueOf(i)); for (int k = 0; k < nav.length; k++) nav[k].setSelectedItem(k == i);
            if (cardRow != null) cardRow.setVisible(i == 0);                 // figure cards belong to the dashboard home only
        }
        /** Opens the Orders page with the given row of the dashboard list selected. */
        private void openOrder(int row) {
            if (row >= 0 && row < orderTable.getRowCount()) { orderTable.setRowSelectionInterval(row, row); orderTable.scrollRectToVisible(orderTable.getCellRect(row, 0, true)); }
            selectPage(1);
        }

        AdminPortal(Runnable onLogout) {
            setLayout(new BorderLayout(14, 0)); setBackground(C_LIGHT); setBorder(new EmptyBorder(10, 14, 10, 14));
            // --- sidebar
            SidebarPanel side = new SidebarPanel(new BorderLayout()); side.setPreferredSize(new Dimension(252, 100)); side.setBorder(new EmptyBorder(18, 12, 16, 12));
            JPanel brand = new JPanel(); brand.setOpaque(false); brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS)); brand.setBorder(new EmptyBorder(0, 10, 16, 0));
            JLabel b1 = label("Admin Console", F_H2.deriveFont(19f), Color.WHITE);
            b1.setAlignmentX(LEFT_ALIGNMENT); brand.add(b1);
            JPanel items = new JPanel(); items.setOpaque(false); items.setLayout(new BoxLayout(items, BoxLayout.Y_AXIS));
            String[] names = {"Dashboard", "Orders", "Inventory & Low Stock", "Profit Estimator", "Popularity"};
            int[] kinds = {AdminArt.HOME, AdminArt.ORDERS, AdminArt.STOCK, AdminArt.PROFIT, AdminArt.POPULAR};
            for (int i = 0; i < 5; i++) { final int idx = i; nav[i] = new NavItem(names[i], kinds[i], () -> selectPage(idx)); items.add(nav[i]); items.add(Box.createVerticalStrut(4)); }
            RoundButton out = secondary("Log out"); out.addActionListener(e -> onLogout.run()); out.setPreferredSize(new Dimension(100, 38));
            JPanel foot = new JPanel(new BorderLayout()); foot.setOpaque(false); foot.add(out, BorderLayout.CENTER);
            side.add(brand, BorderLayout.NORTH); side.add(items, BorderLayout.CENTER); side.add(foot, BorderLayout.SOUTH);

            // --- main column: heading, figure cards, pages
            JPanel head = new JPanel(new BorderLayout()); head.setOpaque(false); head.setBorder(new EmptyBorder(2, 4, 0, 0));
            JPanel ht = new JPanel(); ht.setOpaque(false); ht.setLayout(new BoxLayout(ht, BoxLayout.Y_AXIS));
            JLabel t1 = label("Admin Dashboard", F_H2.deriveFont(24f), C_DARK);
            t1.setAlignmentX(LEFT_ALIGNMENT); ht.add(t1); head.add(ht, BorderLayout.WEST);
            JPanel cards = new JPanel(new GridLayout(1, 4, 12, 0)); cards.setOpaque(false);
            cards.add(cardOrders); cards.add(cardRevenue); cards.add(cardPending); cards.add(cardLow);
            cardRow = cards;
            JPanel top = new JPanel(new BorderLayout(0, 10)); top.setOpaque(false); top.add(head, BorderLayout.NORTH); top.add(cards, BorderLayout.CENTER);
            alertBox.setBorder(new EmptyBorder(10, 16, 10, 16)); alertBox.add(alertLabel, BorderLayout.CENTER);
            pageHost.setOpaque(false);
            pageHost.add(homeTab(), "0"); pageHost.add(ordersTab(), "1"); pageHost.add(stockTab(), "2"); pageHost.add(profitTab(), "3"); pageHost.add(popularityTab(), "4");
            JPanel main = new JPanel(new BorderLayout(0, 12)); main.setOpaque(false); main.add(top, BorderLayout.NORTH); main.add(pageHost, BorderLayout.CENTER);
            add(side, BorderLayout.WEST); add(main, BorderLayout.CENTER);
            // narrow windows: the sidebar shrinks to icons (the names stay available as tooltips), so the tables keep their room
            addComponentListener(new ComponentAdapter() {
                private int mode = -1;
                @Override public void componentResized(ComponentEvent e) {
                    int m = getWidth() < 1240 ? 1 : 0; if (m == mode) return; mode = m;
                    boolean compact = m == 1;
                    side.setPreferredSize(new Dimension(compact ? 84 : 252, 100));
                    b1.setVisible(!compact); brand.setBorder(new EmptyBorder(0, 10, compact ? 4 : 16, 0));
                    for (NavItem n : nav) n.setCompact(compact);
                    out.setText(compact ? "Exit" : "Log out"); out.setPreferredSize(new Dimension(compact ? 60 : 100, 38)); out.setToolTipText(compact ? "Log out" : null);
                    revalidate(); repaint();
                }
            });
            selectPage(0);
            bakery.addListener(this::refreshAll); refreshAll();
        }

        /** Figure cards and sidebar badges, all calculated from the real orders and stock. */
        private void refreshDashboard() {
            int total = 0, pending = 0, completed = 0; double revenue = 0;
            for (Order o : bakery.getOrders()) {
                if (Order.isLegacySample(o)) continue;
                total++; revenue += o.getTotal();
                if (o.getStatus() == OrderStatus.COMPLETED) completed++; else pending++;
            }
            int low = bakery.getLowStock().size();
            cardOrders.set(String.valueOf(total), completed + " completed", C_ACCENT);
            cardRevenue.set(rs(revenue), " ", new Color(0x2F6B3A));
            cardPending.set(String.valueOf(pending), pending == 0 ? "nothing waiting" : " ", new Color(0xB07A12));
            cardLow.set(String.valueOf(low), low == 0 ? "all stock is fine" : (low == 1 ? "needs restocking" : "need restocking"), low == 0 ? new Color(0x2F6B3A) : C_ALERT);
            nav[1].setBadge(pending > 0 ? String.valueOf(pending) : null, new Color(0xB07A12));
            nav[2].setBadge(low > 0 ? String.valueOf(low) : null, C_ALERT);
        }

        // ---------- Orders ----------
        /** Dashboard home: a readable, full-height order list. Details and actions live in the Orders page. */
        private JPanel homeTab() {
            JPanel page = new JPanel(new BorderLayout()); page.setOpaque(false);
            ModernUi.style(homeTable, 0, new int[]{2, 6, 7}, new int[]{4});
            int[] widths = {100, 130, 96, 200, 110, 150, 115, 150};
            for (int i = 0; i < widths.length; i++) homeTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            homeTable.setRowHeight(48);
            homeTable.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) { int r = homeTable.rowAtPoint(e.getPoint()); if (r >= 0) openOrder(r); } }
            });
            homeTable.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openOrder");
            homeTable.getActionMap().put("openOrder", new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { openOrder(homeTable.getSelectedRow()); } });
            RoundButton manage = primary("Manage orders"); manage.setPreferredSize(new Dimension(160, 38));
            manage.setToolTipText("Open the Orders page for full details and actions");
            manage.addActionListener(e -> openOrder(homeTable.getSelectedRow()));
            SoftCard card = new SoftCard(new BorderLayout(0, 10), 12);
            JPanel th = new JPanel(new BorderLayout()); th.setOpaque(false);
            JPanel tl = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0)); tl.setOpaque(false);
            tl.add(ModernUi.title("Orders"));
            th.add(tl, BorderLayout.WEST); th.add(manage, BorderLayout.EAST);
            card.add(th, BorderLayout.NORTH); card.add(ModernUi.scroller(homeTable), BorderLayout.CENTER);
            page.add(card, BorderLayout.CENTER);
            return page;
        }

        private JPanel ordersTab() {
            ModernUi.style(orderTable, 0, new int[]{2, 6, 7}, new int[]{4});
            int[] widths = {100, 120, 96, 140, 112, 150, 115, 150};
            for (int i = 0; i < widths.length; i++) orderTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            orderTable.getSelectionModel().addListSelectionListener(e -> {
                if (e.getValueIsAdjusting()) return;
                showSelectedDetails();
                if (!refreshingOrders && orderTable.getSelectedRow() >= 0)      // bring the details into view when the admin picks an order
                    SwingUtilities.invokeLater(() -> {
                        if (orderDetailCard != null)
                            orderDetailCard.scrollRectToVisible(new Rectangle(0, 0, orderDetailCard.getWidth(), orderDetailCard.getHeight()));
                    });
            });
            // The table has no scroll pane of its own: the whole page (orders + details) scrolls as ONE unit.
            JPanel tableBox = new JPanel(new BorderLayout()); tableBox.setBackground(Color.WHITE);
            tableBox.setBorder(BorderFactory.createLineBorder(new Color(0xEADFDF)));
            tableBox.add(orderTable.getTableHeader(), BorderLayout.NORTH); tableBox.add(orderTable, BorderLayout.CENTER);
            SoftCard tableCard = new SoftCard(new BorderLayout(0, 10), 12);
            JPanel th = new JPanel(new BorderLayout()); th.setOpaque(false);
            th.add(ModernUi.title("Orders"), BorderLayout.WEST); th.add(label("Select an order to manage it", F_SMALL, C_GREY), BorderLayout.EAST);
            tableCard.add(th, BorderLayout.NORTH); tableCard.add(tableBox, BorderLayout.CENTER);

            RoundButton adv = primary("Advance status"), paid = secondary("Mark payment as Paid");
            adv.addActionListener(e -> advance()); paid.addActionListener(e -> markPaid());
            adv.setPreferredSize(new Dimension(160, 38)); paid.setPreferredSize(new Dimension(200, 38));
            adv.setToolTipText("Move the selected order to its next stage"); paid.setToolTipText("Record that the selected order has been paid");
            JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0)); btns.setOpaque(false); btns.add(adv); btns.add(paid);
            orderDetails.setOpaque(false); orderDetails.setLayout(new BoxLayout(orderDetails, BoxLayout.Y_AXIS));
            SoftCard detailCard = new SoftCard(new BorderLayout(0, 10), 12);
            JPanel dTop = new JPanel(new BorderLayout()); dTop.setOpaque(false); dTop.add(ModernUi.title("Order details"), BorderLayout.WEST); dTop.add(btns, BorderLayout.EAST);
            detailCard.add(dTop, BorderLayout.NORTH); detailCard.add(orderDetails, BorderLayout.CENTER);
            orderDetailCard = detailCard;

            WidthTrackingPanel column = new WidthTrackingPanel(new BorderLayout(0, 12)); column.setOpaque(false);
            column.add(tableCard, BorderLayout.NORTH); column.add(detailCard, BorderLayout.CENTER);
            JScrollPane sp = new JScrollPane(column, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            sp.setBorder(null); sp.setOpaque(false); sp.getViewport().setOpaque(false); sp.getVerticalScrollBar().setUnitIncrement(18);
            JPanel page = new JPanel(new BorderLayout()); page.setOpaque(false); page.add(sp, BorderLayout.CENTER);
            return page;
        }
        private Order selectedOrder() { int r = orderTable.getSelectedRow(); return r >= 0 && r < shown.size() ? shown.get(r) : null; }
        private void showSelectedDetails() {
            orderDetails.removeAll();
            Order o = selectedOrder();
            if (o == null) {
                JLabel hint = label("Select an order above to see its full details, including custom cake options and messages.", F_BODY, C_GREY);
                hint.setAlignmentX(Component.LEFT_ALIGNMENT); hint.setBorder(new EmptyBorder(10, 2, 10, 2));
                orderDetails.add(hint);
            } else buildOrderDetails(o);
            orderDetails.revalidate(); orderDetails.repaint();
        }

        // ---- order details layout helpers ----
        private void putDetail(JComponent c, int gapBefore) {
            if (gapBefore > 0) orderDetails.add(Box.createVerticalStrut(gapBefore));
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
            orderDetails.add(c);
        }
        private JComponent detailHeading(String text) {
            JPanel p = new JPanel(new BorderLayout(0, 5)); p.setOpaque(false);
            Font f = F_SMALL.deriveFont(Font.BOLD, 11f).deriveFont(Map.of(java.awt.font.TextAttribute.TRACKING, 0.12f));
            JLabel l = label(text.toUpperCase(Locale.ROOT), f, C_ACCENT);
            JSeparator sep = new JSeparator(); sep.setForeground(new Color(0xE3D8D8));
            p.add(l, BorderLayout.NORTH); p.add(sep, BorderLayout.CENTER);
            return p;
        }
        private JComponent detailRow(String caption, String value) {
            JPanel row = new JPanel(new BorderLayout(12, 0)); row.setOpaque(false); row.setBorder(new EmptyBorder(3, 0, 3, 0));
            JLabel cap = label(caption, F_SMALL.deriveFont(Font.BOLD), C_GREY); cap.setPreferredSize(new Dimension(120, 18));
            row.add(cap, BorderLayout.WEST); row.add(label(value, F_BODY, C_TEXT), BorderLayout.CENTER);
            return row;
        }
        private static String esc(String t) { return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
        private JComponent itemRow(OrderItem it) {
            JPanel row = new JPanel(new BorderLayout(12, 0)); row.setOpaque(false);
            row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xEFE6E6)), new EmptyBorder(7, 0, 7, 0)));
            JLabel qty = label(it.getQty() + " \u00D7", F_BOLD, C_ACCENT); qty.setPreferredSize(new Dimension(44, 18)); qty.setVerticalAlignment(SwingConstants.TOP);
            JPanel mid = new JPanel(); mid.setOpaque(false); mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
            JLabel nm = label(it.getProduct().getName(), F_BOLD, C_DARK); nm.setAlignmentX(Component.LEFT_ALIGNMENT); mid.add(nm);
            String sub;
            if (it.getProduct() instanceof CustomCake) {
                StringBuilder sb = new StringBuilder("<html>");
                String[] parts = it.getProduct().getDescription().split(" \\| ");
                for (int i = 0; i < parts.length; i++) sb.append(i > 0 ? "<br>" : "").append(esc(parts[i]));
                sub = sb.append("</html>").toString();
            } else sub = rs(it.getProduct().getPrice()) + " each";
            JLabel sl = label(sub, F_SMALL, C_GREY); sl.setAlignmentX(Component.LEFT_ALIGNMENT); mid.add(Box.createVerticalStrut(2)); mid.add(sl);
            JLabel price = label(rs(it.getSubtotal()), F_BOLD, C_TEXT); price.setVerticalAlignment(SwingConstants.TOP);
            row.add(qty, BorderLayout.WEST); row.add(mid, BorderLayout.CENTER); row.add(price, BorderLayout.EAST);
            return row;
        }
        private JComponent totalRow(String caption, String value, boolean big) {
            JPanel row = new JPanel(new BorderLayout(12, 0)); row.setOpaque(false); row.setBorder(new EmptyBorder(big ? 6 : 2, 0, big ? 2 : 2, 0));
            row.add(label(caption, big ? F_BOLD.deriveFont(15f) : F_BODY, big ? C_DARK : C_GREY), BorderLayout.WEST);
            row.add(label(value, big ? F_H2.deriveFont(Font.BOLD, 22f) : F_BOLD, big ? C_ACCENT : C_TEXT), BorderLayout.EAST);
            row.setAlignmentX(Component.LEFT_ALIGNMENT); row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
            return row;
        }
        private void buildOrderDetails(Order o) {
            // header: order number + date on the left, status pills on the right
            JPanel head = new JPanel(new BorderLayout(12, 0)); head.setOpaque(false);
            JPanel hl = new JPanel(); hl.setOpaque(false); hl.setLayout(new BoxLayout(hl, BoxLayout.Y_AXIS));
            JLabel id = label("Order " + o.getId(), F_H2.deriveFont(22f), C_DARK); id.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel when = label(new SimpleDateFormat("EEEE, dd MMM yyyy 'at' hh:mm a", Locale.ENGLISH).format(o.getPlacedAt()), F_SMALL, C_GREY);
            when.setAlignmentX(Component.LEFT_ALIGNMENT);
            hl.add(id); hl.add(when);
            JPanel pills = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); pills.setOpaque(false);
            pills.add(new ModernUi.StatusPill(o.getStatusText())); pills.add(new ModernUi.StatusPill(o.getPaymentStatus().toString()));
            head.add(hl, BorderLayout.WEST); head.add(pills, BorderLayout.EAST);
            putDetail(head, 4);

            putDetail(detailHeading("Customer"), 14);
            putDetail(detailRow("Name", o.getCustomerName()), 4);
            putDetail(detailRow("Phone", o.getPhone() == null ? "-" : o.getPhone()), 0);
            putDetail(detailRow("Order type", o.isDelivery() ? "Delivery" : "Pickup from bakery"), 0);
            if (o.isDelivery()) {
                String a = o.getAddress();
                putDetail(detailRow("Address", a == null || a.isBlank() ? "-" : a), 0);
            }
            putDetail(detailRow("Payment", o.getPaymentMethod() + " (" + o.getPaymentStatus() + ")"), 0);

            putDetail(detailHeading("Items"), 16);
            for (OrderItem it : o.getItems()) putDetail(itemRow(it), 0);

            RoundedPanel totals = new RoundedPanel(new BorderLayout(), new Color(0xFAF6F6), 16); totals.setBorder(new EmptyBorder(10, 16, 10, 16));
            JPanel tb = new JPanel(); tb.setOpaque(false); tb.setLayout(new BoxLayout(tb, BoxLayout.Y_AXIS));
            tb.add(totalRow("Items subtotal", rs(o.getSubtotal()), false));
            tb.add(totalRow("Delivery fee", rs(o.getDeliveryFee()), false));
            JSeparator sep = new JSeparator(); sep.setForeground(C_SOFT); sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6)); tb.add(Box.createVerticalStrut(4)); tb.add(sep);
            tb.add(totalRow("Grand total", rs(o.getTotal()), true));
            totals.add(tb, BorderLayout.CENTER);
            putDetail(totals, 14);
            orderDetails.add(Box.createVerticalStrut(4));
        }
        private void advance() {
            Order o = selectedOrder(); if (o == null) { warn(this, "Select an order first."); return; }
            if (o.getStatus() == OrderStatus.COMPLETED) { info(this, "This order is already completed."); return; }
            if (o.getStatus() == OrderStatus.PLACED) { String err = bakery.confirmOrder(o); if (err != null) warn(this, err); return; }
            if (o.getStatus().next() == OrderStatus.COMPLETED && o.getPaymentStatus() == PaymentStatus.PENDING) {
                int r = JOptionPane.showConfirmDialog(this, "Payment is still Pending. Mark as Completed anyway?", "Payment pending", JOptionPane.YES_NO_OPTION);
                if (r != JOptionPane.YES_OPTION) return;
            }
            try {
                bakery.advance(o);
            } catch (IOException ex) {
                warn(this, ex.getMessage());
            }
        }
        private void markPaid() {
            Order o = selectedOrder(); if (o == null) { warn(this, "Select an order first."); return; }
            if (o.getPaymentStatus() == PaymentStatus.PAID || o.getPaymentStatus() == PaymentStatus.DEMO_PAID) {
                info(this, "This order is already paid."); return;
            }
            try {
                bakery.markPaid(o);
            } catch (IOException ex) {
                warn(this, ex.getMessage());
            }
        }
        private void refreshOrders() {
            String selId = selectedOrder() == null ? null : selectedOrder().getId();
            refreshingOrders = true;
            try {
                orderModel.setRowCount(0);
                shown = bakery.getOrders().stream()
                        .filter(o -> !Order.isLegacySample(o))
                        .collect(Collectors.toCollection(ArrayList::new));
                Collections.reverse(shown);
                for (Order o : shown)
                    orderModel.addRow(new Object[]{o.getId(), o.getCustomerName(), o.isDelivery() ? "Delivery" : "Pickup", o.getSummary(),
                            rs(o.getTotal()), o.getPaymentMethod(), o.getPaymentStatus(), o.getStatusText()});
                if (selId != null) for (int i = 0; i < shown.size(); i++)
                    if (shown.get(i).getId().equals(selId)) { orderTable.setRowSelectionInterval(i, i); break; }
            } finally { refreshingOrders = false; }
            showSelectedDetails();
        }

        // ---------- Low stock ----------
        private JPanel stockTab() {
            JPanel page = new JPanel(new BorderLayout(0, 12)); page.setOpaque(false);
            ModernUi.style(stockTable, 0, new int[]{3}, new int[]{});
            stockTable.getColumnModel().getColumn(0).setPreferredWidth(250);
            stockTable.getColumnModel().getColumn(1).setPreferredWidth(130);
            stockTable.getColumnModel().getColumn(2).setPreferredWidth(130);
            RoundButton restock = primary("Restock selected ingredient"); restock.addActionListener(e -> restock());
            restock.setPreferredSize(new Dimension(240, 38));
            JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0)); btns.setOpaque(false);
            btns.add(restock); btns.add(label("Select an ingredient to restock.", F_SMALL, C_GREY));
            SoftCard card = new SoftCard(new BorderLayout(0, 10), 12);
            JPanel th = new JPanel(new BorderLayout()); th.setOpaque(false); th.add(ModernUi.title("Inventory & Low Stock"), BorderLayout.WEST);
            JPanel north = new JPanel(new BorderLayout(0, 10)); north.setOpaque(false); north.add(th, BorderLayout.NORTH); north.add(alertBox, BorderLayout.CENTER);
            card.add(north, BorderLayout.NORTH); card.add(ModernUi.scroller(stockTable), BorderLayout.CENTER); card.add(btns, BorderLayout.SOUTH);
            page.add(card, BorderLayout.CENTER);
            return page;
        }
        private void restock() {
            int r = stockTable.getSelectedRow(); if (r < 0) { warn(this, "Select an ingredient first."); return; }
            String name = (String) stockModel.getValueAt(r, 0);
            Ingredient ing = bakery.getIngredient(name);
            String in = JOptionPane.showInputDialog(this, "How much " + name + " did you purchase" + (ing == null ? "" : " (" + ing.getUnit() + ")") + "?"
                    + (ing != null && ing.isDiscrete() ? "\nWhole numbers only." : ""));
            if (in == null) return;
            try {
                double amt = Double.parseDouble(in.trim());
                if (!Double.isFinite(amt) || amt <= 0) throw new NumberFormatException();
                bakery.restock(name, amt);
            } catch (NumberFormatException ex) {
                warn(this, "Please enter a finite positive number.");
            } catch (IllegalArgumentException ex) {
                warn(this, ex.getMessage());
            } catch (IOException ex) {
                warn(this, "Could not save inventory: " + ex.getMessage());
            }
        }
        private void refreshStock() {
            stockModel.setRowCount(0);
            for (Ingredient i : bakery.getStock())
                stockModel.addRow(new Object[]{i.getName(), i.fmtQty() + " " + i.getUnit(), i.fmtMin() + " " + i.getUnit(), i.isLow() ? "LOW STOCK" : (i.getQty() <= 0 ? "NOT STOCKED" : "OK")});
        }
        private void refreshAlert() {
            List<Ingredient> low = bakery.getLowStock();
            if (low.isEmpty()) {
                alertBox.setFill(C_SOFT); alertLabel.setForeground(C_DARK);
                alertLabel.setText("Stock OK - all ingredients are above their minimum levels.");
            } else {
                String s = low.stream().map(i -> i.getName() + ": " + i.fmtQty() + " " + i.getUnit() + " left (min " + i.fmtMin() + ")").collect(Collectors.joining("   |   "));
                alertBox.setFill(C_ALERT); alertLabel.setForeground(Color.WHITE);
                alertLabel.setText("<html>LOW STOCK ALERT  -  " + s + "</html>");
            }
        }

        // ---------- Profit estimator ----------
        private JPanel profitTab() {
            JPanel page = new JPanel(new BorderLayout(0, 12)); page.setOpaque(false);
            JTable t = new JTable(profitModel);
            ModernUi.style(t, 0, new int[]{}, new int[]{1, 2, 3, 4, 5});
            styleField(overhead); overhead.setPreferredSize(new Dimension(150, 36));
            ((AbstractDocument) overhead.getDocument()).setDocumentFilter(new LimitFilter(18)); overhead.addActionListener(e -> refreshProfit());
            profitNote.setBorder(new EmptyBorder(9, 14, 9, 14)); profitNote.add(profitNoteLabel, BorderLayout.CENTER);
            RoundButton calc = primary("Recalculate"); calc.addActionListener(e -> refreshProfit());
            calc.setPreferredSize(new Dimension(130, 38));
            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0)); top.setOpaque(false);
            top.add(label("Estimated selling / overhead expenses (Rs.):", F_BOLD, C_DARK)); top.add(overhead); top.add(calc);
            JPanel sum = new JPanel(new GridLayout(1, 3, 12, 0)); sum.setOpaque(false);
            sum.add(metric("Total Orders", ordersValue));
            sum.add(metric("Revenue (all orders)", revenueValue));
            sum.add(metric(marginCaption, marginValue));
            SoftCard card = new SoftCard(new BorderLayout(0, 10), 12);
            JPanel north = new JPanel(new BorderLayout(0, 10)); north.setOpaque(false); north.add(ModernUi.title("Profit Estimator"), BorderLayout.NORTH);
            JPanel inputs = new JPanel(new BorderLayout(0, 10)); inputs.setOpaque(false); inputs.add(top, BorderLayout.NORTH); inputs.add(profitNote, BorderLayout.CENTER);
            north.add(inputs, BorderLayout.CENTER);
            card.add(north, BorderLayout.NORTH); card.add(ModernUi.scroller(t), BorderLayout.CENTER);
            page.add(sum, BorderLayout.NORTH); page.add(card, BorderLayout.CENTER);
            return page;
        }
        /** One summary metric: a small caption above a large value, in its own rounded card. */
        private JPanel metric(String caption, JLabel value) { return metric(label(caption, F_SMALL, C_GREY), value); }
        private JPanel metric(JLabel cap, JLabel value) {
            RoundedPanel p = new RoundedPanel(new BorderLayout(0, 2), Color.WHITE, 18); p.setBorder(new EmptyBorder(12, 16, 12, 16));
            cap.setHorizontalAlignment(SwingConstants.CENTER);
            value.setHorizontalAlignment(SwingConstants.CENTER); value.setFont(F_H2.deriveFont(Font.BOLD, 24f)); value.setForeground(C_DARK);
            p.add(cap, BorderLayout.NORTH); p.add(value, BorderLayout.CENTER);
            hoverEffect(p, p);
            return p;
        }
        private void refreshProfit() {
            Map<String, double[]> m = new LinkedHashMap<>();
            double deliveryRevenue = 0; int orderCount = 0;
            for (Order o : bakery.getOrders()) {
                if (Order.isLegacySample(o)) continue;
                orderCount++;
                deliveryRevenue += o.getDeliveryFee();
                for (OrderItem i : o.getItems()) {
                    double[] a = m.computeIfAbsent(i.getProduct().getName(), k -> new double[3]);
                    a[0] += i.getQty(); a[1] += i.getSubtotal(); a[2] += i.getCost();
                }
            }
            List<Map.Entry<String, double[]>> rows = new ArrayList<>(m.entrySet());
            rows.sort((x, y) -> Double.compare(y.getValue()[1] - y.getValue()[2], x.getValue()[1] - x.getValue()[2]));
            profitModel.setRowCount(0); double rev = deliveryRevenue, cost = 0;
            for (Map.Entry<String, double[]> e : rows) {
                double[] a = e.getValue(); double pr = a[1] - a[2]; rev += a[1]; cost += a[2];
                profitModel.addRow(new Object[]{e.getKey(), (int) a[0], rs(a[1]), rs(a[2]), rs(pr), String.format("%.0f%%", a[1] == 0 ? 0 : pr / a[1] * 100)});
            }
            updateProfitSummary(orderCount, rev, cost);
        }

        private void showMargin(String caption, String value, Color color) {
            marginCaption.setText(caption); marginValue.setText(value); marginValue.setForeground(color);
        }
        private void showProfitNote(String text, boolean warning) {
            profitNote.setFill(warning ? C_ALERT : C_TINT);
            profitNoteLabel.setForeground(warning ? Color.WHITE : C_DARK);
            profitNoteLabel.setText("<html>" + text.replace("&", "&amp;").replace("<", "&lt;") + "</html>");
        }
        /** Validates the expense input, then shows either the profit margin or - when expenses exceed the profit - the loss. */
        private void updateProfitSummary(int orderCount, double revenue, double productCost) {
            ordersValue.setText(String.valueOf(orderCount));
            if (!Double.isFinite(revenue) || !Double.isFinite(productCost) || revenue < 0 || productCost < 0) {
                revenueValue.setText("N/A"); showMargin("Profit Margin", "N/A", C_DARK);
                showProfitNote("The revenue figures are out of range, so profit cannot be calculated.", true);
                return;
            }
            revenueValue.setText(rs(revenue));
            String raw = overhead.getText().trim().replace(",", "").replace(" ", "");
            if (raw.regionMatches(true, 0, "Rs.", 0, 3)) raw = raw.substring(3);
            double expenses = 0; String problem = null;
            if (!raw.isEmpty()) {
                try {
                    BigDecimal entered = new BigDecimal(raw);                       // rejects text, NaN and Infinity
                    if (entered.signum() < 0) problem = "Expenses cannot be negative. Enter 0 or a positive amount.";
                    else if (entered.compareTo(BigDecimal.valueOf(MAX_EXPENSE)) > 0)
                        problem = "That expense amount is unrealistically large. Enter an amount up to " + rs(MAX_EXPENSE) + ".";
                    else expenses = entered.doubleValue();
                } catch (NumberFormatException ex) {
                    problem = "Enter the expenses as a plain number, for example 15000.";
                }
            }
            if (problem != null) {
                showMargin("Profit Margin", "N/A", C_DARK);
                showProfitNote("Invalid expenses: " + problem, true);
                return;
            }
            double net = revenue - productCost - expenses;
            if (!Double.isFinite(net)) {
                showMargin("Profit Margin", "N/A", C_DARK);
                showProfitNote("The result is out of range. Please enter smaller expenses.", true);
                return;
            }
            String working = "Revenue " + rs(revenue) + " - product cost " + rs(productCost) + " - expenses " + rs(expenses);
            if (net < 0) {                                                          // a loss: show the amount, not a meaningless huge percentage
                double loss = -net;
                showMargin("Net Loss", rs(loss), C_ALERT);
                showProfitNote(revenue <= 0
                        ? "Warning: there is no revenue yet, so these expenses are a loss of " + rs(loss) + "."
                        : "Warning: expenses exceed your profit. " + working + " = a loss of " + rs(loss) + ".", true);
            } else if (revenue <= 0) {                                              // nothing sold and nothing spent: margin is undefined
                showMargin("Profit Margin", "N/A", C_DARK);
                showProfitNote("No revenue yet, so a profit margin cannot be calculated.", false);
            } else {
                double margin = net / revenue * 100.0;                              // margin = net profit / revenue x 100
                showMargin("Profit Margin", String.format("%.1f%%", margin), C_DARK);
                showProfitNote("Net profit " + rs(net) + "  (" + working + ").", false);
            }
        }

        // ---------- Popularity ----------
        private JPanel popularityTab() {
            JPanel page = new JPanel(new BorderLayout(0, 12)); page.setOpaque(false);
            SoftCard card = new SoftCard(new BorderLayout(0, 10), 12);
            JPanel north = new JPanel(new BorderLayout(0, 8)); north.setOpaque(false);
            north.add(ModernUi.title("Popularity"), BorderLayout.NORTH); north.add(popSummary, BorderLayout.CENTER);
            card.add(north, BorderLayout.NORTH); card.add(ModernUi.scroller(chart), BorderLayout.CENTER);
            card.add(label("Darker bars = top 3 best sellers, grey bars = 3 least ordered products (consider reviewing these).", F_SMALL, C_GREY), BorderLayout.SOUTH);
            page.add(card, BorderLayout.CENTER);
            return page;
        }
        private void refreshPopularity() {
            List<Map.Entry<String, Integer>> list = new ArrayList<>(bakery.unitsSold().entrySet());
            list.sort((a, b) -> b.getValue() - a.getValue());
            chart.setData(list);
            if (!list.isEmpty()) {
                Map.Entry<String, Integer> best = list.get(0), worst = list.get(list.size() - 1);
                popSummary.setText("<html>Most popular: <b>" + best.getKey() + "</b> (" + best.getValue() + " sold)   |   Least popular: <b>"
                        + worst.getKey() + "</b> (" + worst.getValue() + " sold)   |   " + list.size() + " menu products"
                        + "</html>");
            }
        }

        private void refreshAll() { refreshAlert(); refreshOrders(); refreshStock(); refreshProfit(); refreshPopularity(); refreshDashboard(); }
    }

    // =====================================================================
    //  ENTRY POINT
    // =====================================================================
    public static void main(String[] args) {
        installTheme();
        SwingUtilities.invokeLater(() -> new MicroBakeryApp().setVisible(true));
    }

    /**
     * Installs the wine / rose look for everything Swing draws itself (dropdowns and their pop-up lists, scroll bars,
     * tabs, check boxes, radio buttons, spinners, tooltips and the dialog buttons). The custom-painted bakery components
     * take their colours from the C_* constants above.
     */
    static void installTheme() {
        javax.swing.plaf.metal.MetalLookAndFeel.setCurrentTheme(new javax.swing.plaf.metal.OceanTheme() {
            @Override protected javax.swing.plaf.ColorUIResource getPrimary1() { return new javax.swing.plaf.ColorUIResource(C_ACCENT); }   // focus rings, selected borders
            @Override protected javax.swing.plaf.ColorUIResource getPrimary2() { return new javax.swing.plaf.ColorUIResource(C_SOFT); }
            @Override protected javax.swing.plaf.ColorUIResource getPrimary3() { return new javax.swing.plaf.ColorUIResource(0xE6D9D9); }  // text / list selection fill
            @Override protected javax.swing.plaf.ColorUIResource getSecondary1() { return new javax.swing.plaf.ColorUIResource(blend(C_ACCENT, C_DARK, 0.3f)); }   // control borders
            @Override protected javax.swing.plaf.ColorUIResource getSecondary2() { return new javax.swing.plaf.ColorUIResource(C_SOFT); }
            @Override protected javax.swing.plaf.ColorUIResource getSecondary3() { return new javax.swing.plaf.ColorUIResource(C_LIGHT); }
        });
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) { }
        javax.swing.plaf.ColorUIResource top = new javax.swing.plaf.ColorUIResource(blend(C_SOFT, Color.WHITE, 0.65f));
        javax.swing.plaf.ColorUIResource white = new javax.swing.plaf.ColorUIResource(Color.WHITE);
        javax.swing.plaf.ColorUIResource bottom = new javax.swing.plaf.ColorUIResource(blend(C_SOFT, Color.WHITE, 0.3f));
        java.util.List<Object> sheen = Arrays.asList(0.3f, 0.0f, top, white, bottom);      // light rose control sheen
        for (String k : new String[]{"Button.gradient", "ToggleButton.gradient", "CheckBox.gradient", "RadioButton.gradient", "ScrollBar.gradient"})
            UIManager.put(k, sheen);
        UIManager.put("Panel.background", C_LIGHT);
        UIManager.put("Label.foreground", C_TEXT);
        UIManager.put("OptionPane.background", C_LIGHT);
        UIManager.put("OptionPane.messageForeground", C_TEXT);
        UIManager.put("OptionPane.messageFont", F_BODY);   UIManager.put("OptionPane.buttonFont", F_BOLD);
        UIManager.put("TabbedPane.selected", C_SOFT);
        UIManager.put("TabbedPane.contentAreaColor", C_LIGHT);
        UIManager.put("TabbedPane.background", blend(C_SOFT, Color.WHITE, 0.45f));
        UIManager.put("TabbedPane.unselectedBackground", blend(C_SOFT, Color.WHITE, 0.45f));
        UIManager.put("TabbedPane.foreground", C_TEXT);
        UIManager.put("TabbedPane.focus", C_ACCENT);
        UIManager.put("Button.background", C_LIGHT);       UIManager.put("Button.foreground", C_TEXT);
        UIManager.put("Button.select", C_SOFT);            UIManager.put("Button.focus", C_ACCENT);
        UIManager.put("Button.disabledText", blend(C_GREY, C_SOFT, 0.45f));
        for (String k : new String[]{"RadioButton", "CheckBox", "ToggleButton"}) UIManager.put(k + ".disabledText", blend(C_GREY, C_SOFT, 0.3f));
        UIManager.put("ComboBox.background", Color.WHITE); UIManager.put("ComboBox.foreground", C_TEXT);
        UIManager.put("ComboBox.selectionBackground", C_ACCENT); UIManager.put("ComboBox.selectionForeground", Color.WHITE);
        UIManager.put("ComboBox.buttonBackground", C_LIGHT); UIManager.put("ComboBox.buttonShadow", C_SOFT);
        UIManager.put("ComboBox.buttonDarkShadow", C_ACCENT);
        UIManager.put("List.selectionBackground", C_ACCENT); UIManager.put("List.selectionForeground", Color.WHITE);
        for (String k : new String[]{"TextField", "PasswordField", "TextArea", "FormattedTextField", "EditorPane", "TextPane"}) {
            UIManager.put(k + ".selectionBackground", C_ACCENT); UIManager.put(k + ".selectionForeground", Color.WHITE);
            UIManager.put(k + ".caretForeground", C_TEXT);   UIManager.put(k + ".foreground", C_TEXT);
        }
        UIManager.put("Spinner.background", Color.WHITE);  UIManager.put("Spinner.foreground", C_TEXT);
        UIManager.put("ScrollBar.track", C_LIGHT);         UIManager.put("ScrollBar.thumb", C_SOFT);
        UIManager.put("ScrollBar.thumbHighlight", Color.WHITE);
        UIManager.put("ScrollBar.thumbShadow", C_ACCENT);  UIManager.put("ScrollBar.thumbDarkShadow", C_ACCENT);
        UIManager.put("ToolTip.background", C_DARK);       UIManager.put("ToolTip.foreground", Color.WHITE);
        UIManager.put("ToolTip.font", F_SMALL);
        UIManager.put("ToolTip.border", BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(C_ACCENT), new EmptyBorder(3, 6, 3, 6)));
    }
}
