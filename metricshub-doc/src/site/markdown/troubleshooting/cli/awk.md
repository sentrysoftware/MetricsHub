keywords: awk, cli
description: How to execute AWK scripts with MetricsHub AWK CLI.

# AWK CLI Documentation

<!-- MACRO{toc|fromDepth=1|toDepth=2|id=toc} -->

The **MetricsHub AWK CLI** is a command-line utility for executing **AWK scripts** to process and transform text data. It allows users to apply inline AWK scripts or run scripts from a file against given input data or input files.

The AWK CLI is useful for filtering, extracting, and modifying text-based data, including logs, structured reports, and monitoring data.

## Syntax

```bash
awkcli [--script <SCRIPT> | --script-file <SCRIPT FILE>] [--input <INPUT> | --input-file <INPUT FILE>]
```

## Options

| Option          | Description                                                             | Required                                 |
| --------------- | ----------------------------------------------------------------------- | ---------------------------------------- |
| `--script`      | Inline AWK script to execute.                                           | Yes (if `--script-file` is not provided) |
| `--script-file` | Path to an AWK script file to execute.                                  | Yes (if `--script` is not provided)      |
| `--input`       | Input text to process using AWK.                                        | Yes (if `--input-file` is not provided)  |
| `--input-file`  | Path to a file containing the input text.                               | Yes (if `--input` is not provided)       |
| `-v`            | Enables verbose mode. Use `-v` for basic logs, `-vv` for detailed logs. | No                                       |
| `-h, --help`    | Displays detailed help information about available options.             | No                                       |

## **Examples**

### **Example 1: AWK Execution with Inline Script and Input**
Run an **inline AWK script** on a given **input string**:

```bash
awkcli --script "{ print }" --input "AWK Input!"
```

### **Example 2: AWK Execution with Script and Input Files**
Run an **AWK script from a file** on a given **input file**:

```bash
awkcli --script-file script.awk --input-file input.txt
```

#### **Contents of `script.awk`**:
```awk
BEGIN { FS=";" }
{ print $2 }
```

#### **Contents of `input.txt`**:
```
Hardware;Brand;Voltage
Processor;Intel;1.25V
GPU;Nvidia;1.1V
Motherboard;ASUS;12V
RAM;Corsair;1.2V
SSD;Samsung;3.3V
```

#### **Expected Output:**
```
Brand
Intel
Nvidia
ASUS
Corsair
Samsung
```