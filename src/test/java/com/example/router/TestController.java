package com.example.router;

import com.example.router.annotation.PathParam;
import com.example.router.annotation.QueryParam;
import com.example.router.annotation.WildcardParam;

import java.time.LocalDate;

/** Handler target shared by the test classes; records what it received. */
public class TestController {

  public enum Role {
    ADMIN,
    USER,
    GUEST
  }

  public String root() {
    return "root";
  }

  public String listUsers(@QueryParam("page") int page,
                          @QueryParam("active") boolean active) {
    return "users page=" + page + " active=" + active;
  }

  public String getUser(@PathParam("id") long id,
                        @QueryParam("role") Role role,
                        @QueryParam(value = "detail", required = false) Boolean detail) {
    return "user " + id + " role=" + role + " detail=" + detail;
  }

  public String saveUser() {
    return "saved";
  }

  public String staticProfile() {
    return "static-profile";
  }

  public String profileSection(@PathParam("section") String section) {
    return "profile:" + section;
  }

  public String download(@WildcardParam String remainder) {
    return "download:" + remainder;
  }

  public String filesMeta() {
    return "files-meta";
  }

  public String fileId(@PathParam("id") String id) {
    return "file:" + id;
  }

  public String byDate(@QueryParam("day") LocalDate day,
                       @QueryParam(value = "since", required = false,
                           pattern = "dd/MM/yyyy") LocalDate since) {
    return "day=" + day + " since=" + since;
  }

  public String age(@QueryParam(value = "age", required = false) Integer age) {
    return "age=" + age;
  }
}
