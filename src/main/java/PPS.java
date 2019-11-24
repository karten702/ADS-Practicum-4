import utils.Calendar;
import utils.SLF4J;
import utils.XMLParser;
import utils.XMLWriter;

import javax.sound.midi.Soundbank;
import javax.xml.stream.XMLStreamConstants;
import java.security.cert.CertificateParsingException;
import java.time.LocalDate;
import java.time.Month;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class PPS {

    private static Random randomizer = new Random();

    private String name;                // the name of the planning system refers to its xml source file
    private int planningYear;                   // the year indicates the period of start and end dates of the projects
    private Set<Employee> employees;
    private Set<Project> projects;

    @Override
    public String toString() {
        return String.format("PPS_e%d_p%d", this.employees.size(), this.projects.size());
    }

    private PPS() {
        this.name = "none";
        this.planningYear = 2000;
        this.projects = new TreeSet<>();
        this.employees = new TreeSet<>();
    }

    private PPS(String resourceName, int year) {
        this();
        this.name = resourceName;
        this.planningYear = year;
    }

    /**
     * Reports the statistics of the project planning year
     */
    public void printPlanningStatistics() {
        System.out.printf("\nProject Statistics of '%s' in the year %d\n",
                this.name, this.planningYear);
        if (this.employees == null || this.projects == null ||
                this.employees.size() == 0 || this.projects.size() == 0) {
            System.out.println("No employees or projects have been set up...");
            return;
        }

        System.out.printf("%d employees have been assigned to %d projects:\n\n",
                this.employees.size(), this.projects.size());

        System.out.printf("1. The average hourly wage of all employees is %3.2f \n", calculateAverageHourlyWage());
        System.out.printf("2. The longest project is '%s' with '%d' available working days \n", calculateLongestProject(), calculateLongestProject().getNumWorkingDays());
        System.out.printf("3. The following employees have the broadest assignment in no less than %d different projects: \n%s\n",
                calculateMostInvolvedEmployees().stream().findFirst().get().getAssignedProjects().size(),
                calculateMostInvolvedEmployees());
        System.out.printf("4. The total budget of committed project manpower is %d\n", calculateTotalManpowerBudget());
        System.out.printf("5. Below is an overview of total managed budget by junior employees (hourly wage <= 30): \n %s\n",
                calculateManagedBudgetOverview(employee -> employee.getHourlyWage() <= 30 && employee.getManagedProjects().size() > 0));
        System.out.printf("6. Below is an overview of cumulative monthly project spends: \n %s \n",
                calculateCumulativeMonthlySpends());
    }

    /**
     * calculates the average hourly wage of all known employees in this system
     *
     * @return
     */
    public double calculateAverageHourlyWage() {
        return employees.stream().mapToDouble(Employee::getHourlyWage).average().orElse(0.0);
    }

    /**
     * finds the project with the highest number of available working days.
     * (if more than one project with the highest number is found, any one is returned)
     *
     * @return
     */
    public Project calculateLongestProject() {
        return projects.stream().max(Comparator.comparingInt(Project::getNumWorkingDays)).orElse(null);
    }

    /**
     * calculates the total budget for assigned employees across all projects and employees in the system
     * based on the registration of committed hours per day per employee,
     * the number of working days in each project
     * and the hourly rate of each employee
     *
     * @return
     */
    public int calculateTotalManpowerBudget() {
        return projects.stream().mapToInt(Project::calculateManpowerBudget).sum();
    }

    /**
     * finds the employees that are assigned to the highest number of different projects
     * (if multiple employees are assigned to the same highest number of projects,
     * all these employees are returned in the set)
     *
     * @return
     */
    public Set<Employee> calculateMostInvolvedEmployees() {
        int highest = employees.stream()
                .max(Comparator.comparingInt(value -> value.getAssignedProjects().size()))
                .orElse(new Employee())
                .getAssignedProjects().size();
        return employees.stream()
                .filter(employee -> employee.getAssignedProjects().size() == highest)
                .collect(Collectors.toSet());
    }

    /**
     * Calculates an overview of total managed budget per employee that complies with the filter predicate
     * The total managed budget of an employee is the sum of all man power budgets of all projects
     * that are being managed by this employee
     *
     * @param filter
     * @return
     */
    public Map<Employee, Integer> calculateManagedBudgetOverview(Predicate<Employee> filter) {
        return employees.stream()
                .filter(filter)
                .collect(Collectors.toMap(e -> e, Employee::calculateManagedBudget, Math::addExact));
    }

    /**
     * Calculates and overview of total monthly spends across all projects in the system
     * The monthly spend of a single project is the accumulated manpower cost of all employees assigned to the
     * project across all working days in the month.
     *
     * @return
     */
    public Map<Month, Integer> calculateCumulativeMonthlySpends() {
        Map<Month, Integer> monthlySpends = new TreeMap<>();
        projects.forEach(project -> {
            int costPerDay = project.getCommittedHoursPerDay()
                    .entrySet()
                    .stream()
                    .mapToInt(employeeIntegerEntry -> employeeIntegerEntry.getKey().getHourlyWage() * employeeIntegerEntry.getValue())
                    .sum();

            project.getWorkingDays().forEach(localDate -> {
                monthlySpends.merge(localDate.getMonth(), costPerDay, Math::addExact);
            });
        });

        //Map<Project, Integer> projectHours = projects.stream().collect(Collectors.toMap(project -> project, project -> project.getCommittedHoursPerDay().values().stream().mapToInt(integer -> integer).sum(), Math::addExact));
        //Map<Month, Integer> daysAMonth = projects.stream().flatMap(project -> project.getWorkingDays().stream()).collect(Collectors.toMap(localDate -> localDate.getMonth(), 1, Math::addExact));

        //projects.stream().flatMapToInt(project -> (IntStream) project.getCommittedHoursPerDay().values().stream()).sum();


        // .collect(Collectors.toMap(, 1, Math::addExact));  //.collect(Collectors.toMap(t -> t., 1, Math::addExact));

        //.forEach(project -> Calendar.getWorkingDays(project.getStartDate(), project.getEndDate()).forEach(localDate -> localDate.getMonth()));

        return monthlySpends;
    }

    public String getName() {
        return name;
    }

    /**
     * A builder helper class to compose a small PPS using method-chaining of builder methods
     */
    public static class Builder {
        PPS pps;

        public Builder() {
            this.pps = new PPS();
        }

        /**
         * Add another employee to the PPS being build
         *
         * @param employee
         * @return
         */
        public Builder addEmployee(Employee employee) {
            if (employee != null)
                pps.employees.add(employee);

            return this;
        }

        /**
         * Add another project to the PPS
         * register the specified manager as the manager of the new
         *
         * @param project
         * @param manager
         * @return
         */
        public Builder addProject(Project project, Employee manager) {
            if (project != null && manager != null) {
                Employee foundEmployee = pps.employees.stream().filter(employee -> employee.equals(manager)).findFirst().orElse(manager);
                pps.projects.add(project);
                pps.employees.add(foundEmployee);
                foundEmployee.getManagedProjects().add(project);
                return this;
            }
            return this;
        }

        /**
         * Add a commitment to work hoursPerDay on the project that is identified by projectCode
         * for the employee who is identified by employeeNr
         * This commitment is added to any other commitment that the same employee already
         * has got registered on the same project,
         *
         * @param projectCode
         * @param employeeNr
         * @param hoursPerDay
         * @return
         */
        public Builder addCommitment(String projectCode, int employeeNr, int hoursPerDay) {
            Project projectToAddTo = pps.projects.stream()
                    .filter(project -> project.getCode().equals(projectCode))
                    .findFirst()
                    .orElse(null);
            if(projectToAddTo != null) {
                Employee employeeForNumber = pps.employees.stream()
                        .filter(employee -> employee.getNumber() == employeeNr)
                        .findFirst()
                        .orElse(new Employee(employeeNr));
                pps.employees.add(employeeForNumber);
                projectToAddTo.addCommitment(employeeForNumber, hoursPerDay);
            }
            return this;
        }

        /**
         * Complete the PPS being build
         *
         * @return
         */
        public PPS build() {
            return this.pps;
        }
    }

    public Set<Project> getProjects() {
        return this.projects;
    }

    public Set<Employee> getEmployees() {
        return this.employees;
    }

    /**
     * Loads a complete configuration from an XML file
     *
     * @param resourceName the XML file name to be found in the resources folder
     * @return
     */
    public static PPS importFromXML(String resourceName) {
        XMLParser xmlParser = new XMLParser(resourceName);

        try {
            xmlParser.nextTag();
            xmlParser.require(XMLStreamConstants.START_ELEMENT, null, "projectPlanning");
            int year = xmlParser.getIntegerAttributeValue(null, "year", 2000);
            xmlParser.nextTag();

            PPS pps = new PPS(resourceName, year);

            Project.importProjectsFromXML(xmlParser, pps.projects);
            Employee.importEmployeesFromXML(xmlParser, pps.employees, pps.projects);

            return pps;

        } catch (Exception ex) {
            SLF4J.logException("XML error in '" + resourceName + "'", ex);
        }

        return null;
    }
}
