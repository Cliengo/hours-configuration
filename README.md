# Hours Configuration

Utility library used to configure hour ranges for valid actions.

## Installation

Still figuring this out. Will involve Maven though.

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

## Contributing
Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

Please make sure to update tests as appropriate.
