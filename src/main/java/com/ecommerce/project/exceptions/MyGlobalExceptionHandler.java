package com.ecommerce.project.exceptions;


import com.ecommerce.project.payload.APIResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.boot.context.config.ConfigDataResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class MyGlobalExceptionHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<APIResponse> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().iterator().next().getMessage();
        return new ResponseEntity<>(new APIResponse(message, false), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> myMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        Map<String,String> response = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(err -> {
            String fieldName =((FieldError)err).getField();
            String message =err.getDefaultMessage();
            response.put(fieldName,message);
        });
        return new ResponseEntity<Map<String,String>>(response, HttpStatus.BAD_REQUEST);
    }

//    @ExceptionHandler(ResourceNotFoundException.class)
//    public ResponseEntity<APIResponse> myResourceNotFoundException(ResourceNotFoundException e) {
//        String message = e.getMessage();
//        APIResponse apiResponse = new APIResponse(message, false);
//       return new ResponseEntity<>(apiResponse,HttpStatus.NOT_FOUND);
//    }

    @ExceptionHandler(APIException.class)
    public ResponseEntity<APIResponse> myAPIException(APIException e) {
        String message = e.getMessage();
        APIResponse apiResponse = new APIResponse(message,false);
        return new ResponseEntity<>(apiResponse,HttpStatus.BAD_REQUEST);
    }

    // This tells Spring to run this method whenever a ResourceNotFoundException is thrown
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> resourceNotFoundExceptionHandler(ResourceNotFoundException ex) {

        // Create a custom JSON response body using a Map
        Map<String, Object> response = new HashMap<>();

        // ex.getMessage() will grab the "Order not found with orderId : X" string you created
        response.put("message", ex.getMessage());
        response.put("success", false);
        response.put("status", HttpStatus.NOT_FOUND.value());

        // Return the custom Map with a 404 status code
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }



}
