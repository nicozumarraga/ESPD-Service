#!/usr/bin/env node

const fs = require('fs');

function fixDateFormat(obj) {
    if (obj === null || obj === undefined) {
        return obj;
    }
    
    // Check if this is a date object with centuryOfEra field
    if (typeof obj === 'object' && 'centuryOfEra' in obj && 'year' in obj && 'monthOfYear' in obj && 'dayOfMonth' in obj) {
        // Convert to timestamp
        const date = new Date(obj.year, obj.monthOfYear - 1, obj.dayOfMonth);
        return date.getTime();
    }
    
    // Recursively process arrays
    if (Array.isArray(obj)) {
        return obj.map(item => fixDateFormat(item));
    }
    
    // Recursively process objects
    if (typeof obj === 'object') {
        const fixed = {};
        for (const key in obj) {
            if (obj.hasOwnProperty(key)) {
                fixed[key] = fixDateFormat(obj[key]);
            }
        }
        return fixed;
    }
    
    // Return primitive values as-is
    return obj;
}

// Read the input file
const inputFile = 'api-espd-export-request.json';
const outputFile = 'api-espd-export-request-fixed.json';

try {
    const data = JSON.parse(fs.readFileSync(inputFile, 'utf8'));
    
    // Fix date formats
    const fixedData = fixDateFormat(data);
    
    // Write the fixed data
    fs.writeFileSync(outputFile, JSON.stringify(fixedData, null, 2), 'utf8');
    
    console.log(`Fixed JSON file created: ${outputFile}`);
    console.log('\nExample of date transformation:');
    console.log('Before: Complex object with centuryOfEra, year, monthOfYear, etc.');
    console.log('After: Simple timestamp in milliseconds (e.g., 1754611200000)');
    
} catch (error) {
    console.error('Error processing file:', error.message);
    process.exit(1);
}