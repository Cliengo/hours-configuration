# Hours Configuration

Utility library used to configure hour ranges for valid actions.

## Installation

Add the repository for the library in your Play! application:

```yaml
- hours-configuration:
    type: http
    artifact: "https://github.com/Cliengo/hours-configuration/raw/master/releases/[revision]/hours-configuration-[revision].jar"
    contains:
        - com.cliengo -> hours-configuration
```

Add the dependency itself:

```yaml
- com.cliengo -> hours-configuration 1.+ # This downloads version 1.x.y
```

## Usage

```java
import com.cliengo.HoursConfiguration;
import org.joda.time.DateTimeZone;

class MyClass {
    public static void main(String[] args){
      String singleDayConfig = "11-13";
      String[] allWeekConfig = HoursConfiguration.copyForAllDays(singleDayConfig);
      boolean isValid = HoursConfiguration.isValid(allWeekConfig); // True

      // Result depends on current system time, in this case we force UTC as timezone
      if (HoursConfiguration.isAllowedNow(allWeekConfig, DateTimeZone.UTC)) {
        System.out.println("We are open!");
      } else {
        System.out.println("We are closed.");
      }
    }
}
```

## Building

`mvn package`, will run tests first and output a JAR with the current version under `releases/[version]`.

## Testing

`mvn test`

## Contributing
Pull requests are welcome. To implement something new:

1. Do your thing in a new branch.
1. Update/add tests as appropriate.
1. Increment `version` field in `pom.xml` **according to [SemVer](https://semver.org/#summary)**.
1. Build (see [Building](#building))
1. Make sure you push the new JAR!
1. Create PR.
